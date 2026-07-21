package com.pspyouxi.converter;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "PSPConverter";

    // Format constants
    private static final int FORMAT_CSO = 0; // ISO -> CSO
    private static final int FORMAT_ISO = 1; // CSO -> ISO

    // Views
    private TextView tvFolderPath;
    private Spinner spinnerFormat;
    private Spinner spinnerFiles;
    private TextView tvFileLabel;
    private MaterialButton btnSelectFolder;
    private MaterialButton btnConvert;
    private MaterialButton btnPermission;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private TextView tvStatus;
    private TextView tvCurrentFile;
    private TextView tvFileInfo;
    private TextView tvPermissionWarning;
    private View progressSection;

    // State
    private String selectedFolderPath = null;
    private String selectedFilePath = null;
    private int selectedFormat = FORMAT_CSO;
    private boolean isConverting = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // File list in selected folder
    private List<String> imageFileNames = new ArrayList<>();
    private List<String> imageFilePaths = new ArrayList<>();

    // Activity result launcher for folder selection
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null && getActivity() != null) {
                        getActivity().getContentResolver().takePersistableUriPermission(
                                treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );

                        String path = getRealPathFromUri(treeUri);
                        Log.d(TAG, "Folder selected - URI: " + treeUri + ", docId: " + DocumentsContract.getTreeDocumentId(treeUri) + ", realPath: " + path);
                        if (path != null) {
                            selectedFolderPath = path;
                            selectedFilePath = null;
                            tvFolderPath.setText(path);
                            populateFileSpinner();
                            updateFileInfo();
                        } else {
                            selectedFolderPath = null;
                            tvFolderPath.setText(treeUri.getPath());
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "请选择内部存储中的文件夹", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSpinners();
        setupListeners();
        checkPermissions();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void initViews(View view) {
        tvFolderPath = view.findViewById(R.id.tvFolderPath);
        spinnerFormat = view.findViewById(R.id.spinnerFormat);
        spinnerFiles = view.findViewById(R.id.spinnerFiles);
        tvFileLabel = view.findViewById(R.id.tvFileLabel);
        btnSelectFolder = view.findViewById(R.id.btnSelectFolder);
        btnConvert = view.findViewById(R.id.btnConvert);
        btnPermission = view.findViewById(R.id.btnPermission);
        progressBar = view.findViewById(R.id.progressBar);
        tvProgress = view.findViewById(R.id.tvProgress);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvCurrentFile = view.findViewById(R.id.tvCurrentFile);
        tvFileInfo = view.findViewById(R.id.tvFileInfo);
        tvPermissionWarning = view.findViewById(R.id.tvPermissionWarning);
        progressSection = view.findViewById(R.id.progressSection);
    }

    private void setupSpinners() {
        // Format spinner
        String[] formats = {"CSO（压缩ISO）", "ISO（解压CSO）"};
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(getContext(),
                R.layout.item_spinner, formats);
        formatAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerFormat.setAdapter(formatAdapter);

        spinnerFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFormat = position;
                // Re-populate file spinner when format changes
                if (selectedFolderPath != null) {
                    populateFileSpinner();
                }
                updateFileInfo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // File spinner - initially empty
        ArrayAdapter<String> fileAdapter = new ArrayAdapter<>(getContext(),
                R.layout.item_spinner, new ArrayList<>());
        fileAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerFiles.setAdapter(fileAdapter);

        spinnerFiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < imageFilePaths.size()) {
                    selectedFilePath = imageFilePaths.get(position);
                    Log.d(TAG, "File selected from spinner: " + selectedFilePath);
                    updateFileInfo();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedFilePath = null;
            }
        });
    }

    private void populateFileSpinner() {
        if (selectedFolderPath == null) {
            tvFileLabel.setVisibility(View.GONE);
            spinnerFiles.setVisibility(View.GONE);
            return;
        }

        File folder = new File(selectedFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            tvFileLabel.setVisibility(View.GONE);
            spinnerFiles.setVisibility(View.GONE);
            return;
        }

        // Get files based on current format selection
        String extension = selectedFormat == FORMAT_CSO ? ".iso" : ".cso";
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(extension));

        imageFileNames.clear();
        imageFilePaths.clear();

        if (files != null && files.length > 0) {
            for (File file : files) {
                imageFileNames.add(file.getName() + " (" + formatFileSize(file.length()) + ")");
                imageFilePaths.add(file.getAbsolutePath());
            }

            // Show the file spinner
            tvFileLabel.setVisibility(View.VISIBLE);
            spinnerFiles.setVisibility(View.VISIBLE);

            ArrayAdapter<String> fileAdapter = new ArrayAdapter<>(getContext(),
                    R.layout.item_spinner, imageFileNames);
            fileAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spinnerFiles.setAdapter(fileAdapter);

            // Auto-select first file
            if (imageFilePaths.size() > 0) {
                selectedFilePath = imageFilePaths.get(0);
            }
        } else {
            // No files found - hide spinner
            tvFileLabel.setVisibility(View.GONE);
            spinnerFiles.setVisibility(View.GONE);
            selectedFilePath = null;
        }
    }

    private void setupListeners() {
        btnSelectFolder.setOnClickListener(v -> openFolderPicker());
        btnPermission.setOnClickListener(v -> requestAllFilesAccess());

        btnConvert.setOnClickListener(v -> {
            if (!isConverting) {
                startConversion();
            } else {
                Toast.makeText(getContext(), "正在转换中，请等待…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            Log.d(TAG, "Android 11+ - MANAGE_EXTERNAL_STORAGE granted: " + hasPermission);
            updatePermissionStatus();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasRead = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasWrite = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 6-10 - READ: " + hasRead + ", WRITE: " + hasWrite);
            if (!hasRead || !hasWrite) {
                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 1001);
            }
            updatePermissionStatus();
        }
    }

    private void updatePermissionStatus() {
        boolean hasPermission = hasWritePermission();
        Log.d(TAG, "updatePermissionStatus - hasWritePermission: " + hasPermission);

        if (btnPermission != null) {
            btnPermission.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        }
        if (tvPermissionWarning != null) {
            tvPermissionWarning.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
            if (!hasPermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    tvPermissionWarning.setText("⚠️ 需要授予所有文件访问权限才能读写游戏文件");
                } else {
                    tvPermissionWarning.setText("⚠️ 需要存储权限才能读写游戏文件");
                }
            }
        }
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(getContext())
                        .setTitle("需要存储权限")
                        .setMessage("此应用需要\"所有文件访问权限\"才能读取和写入游戏文件。\n\n请在设置页面中找到本应用并开启权限。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasWrite = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasWrite) {
                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
            updatePermissionStatus();
        }
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }

    private String getRealPathFromUri(Uri uri) {
        String docId = DocumentsContract.getTreeDocumentId(uri);
        if (docId != null && docId.startsWith("primary:")) {
            String relativePath = docId.substring("primary:".length());
            return "/sdcard/" + relativePath;
        }
        if (docId != null) {
            String[] parts = docId.split(":");
            if (parts.length >= 2) {
                return "/sdcard/" + parts[1];
            }
        }
        return null;
    }

    private void updateFileInfo() {
        if (selectedFilePath != null) {
            // A file is selected from the spinner
            File file = new File(selectedFilePath);
            if (file.exists() && file.canRead()) {
                String name = file.getName();
                String ext = name.substring(name.lastIndexOf(".")).toLowerCase();
                boolean isCorrectFormat = (selectedFormat == FORMAT_CSO && ext.equals(".iso"))
                        || (selectedFormat == FORMAT_ISO && ext.equals(".cso"));
                if (isCorrectFormat) {
                    tvFileInfo.setVisibility(View.VISIBLE);
                    tvFileInfo.setText(String.format("已选择: %s (%s)", name, formatFileSize(file.length())));
                    btnConvert.setEnabled(true);
                } else {
                    tvFileInfo.setVisibility(View.VISIBLE);
                    String needed = selectedFormat == FORMAT_CSO ? ".iso" : ".cso";
                    tvFileInfo.setText("当前文件格式不匹配，需要选择" + needed + "文件");
                    btnConvert.setEnabled(false);
                }
            } else {
                tvFileInfo.setVisibility(View.VISIBLE);
                tvFileInfo.setText("文件不存在或无法读取");
                btnConvert.setEnabled(false);
            }
            return;
        }

        // No file selected
        if (selectedFolderPath == null) {
            tvFileInfo.setVisibility(View.GONE);
            btnConvert.setEnabled(false);
        } else {
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText("请从下拉列表中选择镜像文件");
            btnConvert.setEnabled(false);
        }
    }

    private boolean hasWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startConversion() {
        if (!hasWritePermission()) {
            Log.w(TAG, "No write permission - cannot convert");
            new AlertDialog.Builder(getContext())
                    .setTitle("需要存储权限")
                    .setMessage("转换文件需要写入权限。请先授予\"所有文件访问权限\"。")
                    .setPositiveButton("去设置", (dialog, which) -> requestAllFilesAccess())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        if (selectedFilePath != null) {
            startSingleFileConversion();
        } else {
            Toast.makeText(getContext(), "请先选择镜像文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSingleFileConversion() {
        File file = new File(selectedFilePath);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(getContext(), "文件不存在或无法读取", Toast.LENGTH_SHORT).show();
            return;
        }

        String inputPath = file.getAbsolutePath();
        String outputPath;

        if (selectedFormat == FORMAT_CSO) {
            outputPath = inputPath.substring(0, inputPath.length() - 4) + ".cso";
        } else {
            outputPath = inputPath.substring(0, inputPath.length() - 4) + ".iso";
        }

        Log.d(TAG, "Single file conversion: " + inputPath + " -> " + outputPath);

        isConverting = true;
        progressSection.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);
        btnConvert.setText("转换中…");
        progressBar.setProgress(0);
        tvProgress.setText("0%");

        new Thread(() -> convertSingleFile(inputPath, outputPath, file.getName())).start();
    }

    private void convertSingleFile(String inputPath, String outputPath, String fileName) {
        if (new File(outputPath).exists()) {
            mainHandler.post(() -> {
                tvCurrentFile.setText(fileName + " (已存在，跳过)");
                finishConversion(1, 0);
            });
            return;
        }

        CsoConverter.ProgressCallback callback = new CsoConverter.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String name) {
                int progress = (int)((float)current / total * 100);
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    tvCurrentFile.setText(name);
                    progressBar.setProgress(progress);
                    tvProgress.setText(progress + "%");
                });
            }

            @Override
            public void onComplete(String outPath, long outputSize) {
                Log.d(TAG, "Conversion complete: " + outPath + " (" + outputSize + " bytes)");
                mainHandler.post(() -> {
                    String sizeStr = formatFileSize(outputSize);
                    tvStatus.setText(fileName + " → 完成 (" + sizeStr + ")");
                });
                finishConversion(1, 0);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Conversion error for " + fileName + ": " + message);
                mainHandler.post(() -> {
                    tvStatus.setText(fileName + " → 失败: " + message);
                });
                finishConversion(0, 1);
            }
        };

        try {
            if (selectedFormat == FORMAT_CSO) {
                CsoConverter.iso2cso(inputPath, outputPath, 9, 0, callback);
            } else {
                CsoConverter.cso2iso(inputPath, outputPath, callback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error converting " + fileName, e);
            mainHandler.post(() -> {
                tvStatus.setText(fileName + " → 异常: " + e.getMessage());
            });
            finishConversion(0, 1);
        }
    }

    private void finishConversion(int successCount, int failCount) {
        mainHandler.post(() -> {
            if (getContext() == null || getView() == null) return;
            isConverting = false;
            progressBar.setProgress(100);
            tvProgress.setText("100%");

            String resultMsg = String.format("转换完成！成功 %d 个，失败 %d 个", successCount, failCount);
            tvStatus.setText(resultMsg);
            btnConvert.setEnabled(true);
            btnConvert.setText("🚀 开始转换");

            Toast.makeText(getContext(), resultMsg, Toast.LENGTH_LONG).show();
        });
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}