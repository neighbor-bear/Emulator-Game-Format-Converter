package com.pspyouxi.converter;

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
import android.view.View;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1001;
    private static final String TAG = "PSPConverter";

    // Format constants
    private static final int FORMAT_CSO = 0; // ISO -> CSO
    private static final int FORMAT_ISO = 1; // CSO -> ISO

    // Selection mode
    private static final int MODE_FOLDER = 0;
    private static final int MODE_FILE = 1;

    // Views
    private TextView tvFolderPath;
    private Spinner spinnerFormat;
    private MaterialButton btnSelectFolder;
    private MaterialButton btnSelectFile;
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
    private int selectionMode = MODE_FOLDER;
    private boolean isConverting = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Activity result launcher for folder selection
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        getContentResolver().takePersistableUriPermission(
                                treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );

                        String path = getRealPathFromUri(treeUri);
                        Log.d(TAG, "Folder selected - URI: " + treeUri + ", docId: " + DocumentsContract.getTreeDocumentId(treeUri) + ", realPath: " + path);
                        if (path != null) {
                            selectedFolderPath = path;
                            selectedFilePath = null;
                            selectionMode = MODE_FOLDER;
                            tvFolderPath.setText(path);
                            updateFileInfo();
                        } else {
                            selectedFolderPath = null;
                            tvFolderPath.setText(treeUri.getPath());
                            Toast.makeText(this, "请选择内部存储中的文件夹", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });

    // Activity result launcher for file selection
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null) {
                        String path = getFilePathFromUri(fileUri);
                        Log.d(TAG, "File selected - URI: " + fileUri + ", realPath: " + path);
                        if (path != null) {
                            selectedFilePath = path;
                            selectedFolderPath = null;
                            selectionMode = MODE_FILE;
                            tvFolderPath.setText(path);
                            updateFileInfo();
                        } else {
                            Toast.makeText(this, "请选择内部存储中的文件", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity onCreate - Android " + Build.VERSION.SDK_INT);

        initViews();
        setupSpinner();
        setupListeners();
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check permission when returning from settings
        updatePermissionStatus();
    }

    private void initViews() {
        tvFolderPath = findViewById(R.id.tvFolderPath);
        spinnerFormat = findViewById(R.id.spinnerFormat);
        btnSelectFolder = findViewById(R.id.btnSelectFolder);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnConvert = findViewById(R.id.btnConvert);
        btnPermission = findViewById(R.id.btnPermission);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);
        tvStatus = findViewById(R.id.tvStatus);
        tvCurrentFile = findViewById(R.id.tvCurrentFile);
        tvFileInfo = findViewById(R.id.tvFileInfo);
        tvPermissionWarning = findViewById(R.id.tvPermissionWarning);
        progressSection = findViewById(R.id.progressSection);
    }

    private void setupSpinner() {
        String[] formats = {"CSO（压缩ISO）", "ISO（解压CSO）"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_spinner, formats);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerFormat.setAdapter(adapter);

        spinnerFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFormat = position;
                updateFileInfo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        btnSelectFolder.setOnClickListener(v -> openFolderPicker());
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        btnPermission.setOnClickListener(v -> requestAllFilesAccess());

        btnConvert.setOnClickListener(v -> {
            if (!isConverting) {
                startConversion();
            } else {
                Toast.makeText(this, "正在转换中，请等待…", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            Log.d(TAG, "Android 11+ - MANAGE_EXTERNAL_STORAGE granted: " + hasPermission);
            updatePermissionStatus();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 6-10 - READ: " + hasRead + ", WRITE: " + hasWrite);
            if (!hasRead || !hasWrite) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_PERMISSION);
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
                new AlertDialog.Builder(this)
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
            // Android 6-10: request runtime permissions
            boolean hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!hasWrite) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
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

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Allow selecting ISO or CSO files
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        filePickerLauncher.launch(intent);
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

    private String getFilePathFromUri(Uri uri) {
        // Try to get file path from document URI
        String docId = DocumentsContract.getDocumentId(uri);
        Log.d(TAG, "getFilePathFromUri - docId: " + docId);
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
        if (selectionMode == MODE_FILE && selectedFilePath != null) {
            // Single file mode
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

        // Folder mode
        if (selectedFolderPath == null) {
            tvFileInfo.setVisibility(View.GONE);
            return;
        }

        File folder = new File(selectedFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            tvFileInfo.setVisibility(View.GONE);
            return;
        }

        String extension = selectedFormat == FORMAT_CSO ? ".iso" : ".cso";
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(extension));

        Log.d(TAG, "updateFileInfo - path: " + selectedFolderPath + ", extension: " + extension + ", files: " + (files != null ? files.length : "null"));

        if (files != null && files.length > 0) {
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText(String.format("找到 %d 个%s文件可转换", files.length, extension.toUpperCase()));
            btnConvert.setEnabled(true);
            for (File f : files) {
                Log.d(TAG, "  Found file: " + f.getAbsolutePath() + " (" + f.length() + " bytes)");
            }
        } else {
            tvFileInfo.setVisibility(View.VISIBLE);
            tvFileInfo.setText(R.string.no_files_found);
            btnConvert.setEnabled(false);
        }
    }

    private boolean hasWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startConversion() {
        // Check write permission first
        if (!hasWritePermission()) {
            Log.w(TAG, "No write permission - cannot convert");
            new AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("转换文件需要写入权限。请先授予\"所有文件访问权限\"。")
                    .setPositiveButton("去设置", (dialog, which) -> requestAllFilesAccess())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        if (selectionMode == MODE_FILE && selectedFilePath != null) {
            // Single file mode
            startSingleFileConversion();
        } else if (selectedFolderPath != null) {
            // Folder mode
            startFolderConversion();
        } else {
            Toast.makeText(this, "请先选择文件或文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSingleFileConversion() {
        File file = new File(selectedFilePath);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "文件不存在或无法读取", Toast.LENGTH_SHORT).show();
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

        // Show progress section
        isConverting = true;
        progressSection.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);
        btnConvert.setText("转换中…");
        progressBar.setProgress(0);
        tvProgress.setText("0%");

        new Thread(() -> convertSingleFile(inputPath, outputPath, file.getName())).start();
    }

    private void convertSingleFile(String inputPath, String outputPath, String fileName) {
        // Skip if output already exists
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

    private void startFolderConversion() {
        File folder = new File(selectedFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            Log.e(TAG, "Folder does not exist or is not a directory: " + selectedFolderPath);
            Toast.makeText(this, "文件夹不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        String extension = selectedFormat == FORMAT_CSO ? ".iso" : ".cso";
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(extension));

        if (files == null || files.length == 0) {
            Log.w(TAG, "No " + extension + " files found in: " + selectedFolderPath);
            Toast.makeText(this, R.string.no_files_found, Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting conversion - format: " + (selectedFormat == FORMAT_CSO ? "ISO->CSO" : "CSO->ISO") + ", files: " + files.length);

        isConverting = true;
        progressSection.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);
        btnConvert.setText("转换中…");
        progressBar.setProgress(0);
        tvProgress.setText("0%");

        new Thread(() -> convertFiles(files)).start();
    }

    private void convertFiles(File[] files) {
        int totalFiles = files.length;
        final int[] completedCount = {0};
        final int[] failedCount = {0};

        Log.d(TAG, "convertFiles started - totalFiles: " + totalFiles);

        try {
            for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
                File file = files[fileIndex];
                String fileName = file.getName();
                String inputPath = file.getAbsolutePath();
                String outputPath;

                if (selectedFormat == FORMAT_CSO) {
                    outputPath = inputPath.substring(0, inputPath.length() - 4) + ".cso";
                } else {
                    outputPath = inputPath.substring(0, inputPath.length() - 4) + ".iso";
                }

                Log.d(TAG, "Converting file " + (fileIndex + 1) + "/" + totalFiles + ": " + inputPath + " -> " + outputPath);

                // Skip if output already exists
                if (new File(outputPath).exists()) {
                    final String fn = fileName;
                    Log.d(TAG, "Output already exists, skipping: " + outputPath);
                    mainHandler.post(() -> tvCurrentFile.setText(fn + " (已存在，跳过)"));
                    completedCount[0]++;
                    continue;
                }

                // Verify input file is readable
                if (!file.canRead()) {
                    Log.e(TAG, "Cannot read input file: " + inputPath);
                    failedCount[0]++;
                    final String fn = fileName;
                    mainHandler.post(() -> tvStatus.setText(fn + " → 失败: 无法读取文件"));
                    continue;
                }

                final int currentFileIndex = fileIndex;

                CsoConverter.ProgressCallback callback = new CsoConverter.ProgressCallback() {
                    @Override
                    public void onProgress(int current, int total, String name) {
                        int overallProgress = (int)(((currentFileIndex + (float)current / total) / totalFiles) * 100);
                        mainHandler.post(() -> {
                            tvCurrentFile.setText(name);
                            progressBar.setProgress(overallProgress);
                            tvProgress.setText(overallProgress + "%");
                        });
                    }

                    @Override
                    public void onComplete(String outPath, long outputSize) {
                        Log.d(TAG, "Conversion complete: " + outPath + " (" + outputSize + " bytes)");
                        completedCount[0]++;
                        mainHandler.post(() -> {
                            String sizeStr = formatFileSize(outputSize);
                            tvStatus.setText(fileName + " → 完成 (" + sizeStr + ")");
                        });
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Conversion error for " + fileName + ": " + message);
                        failedCount[0]++;
                        mainHandler.post(() -> tvStatus.setText(fileName + " → 失败: " + message));
                    }
                };

                if (selectedFormat == FORMAT_CSO) {
                    CsoConverter.iso2cso(inputPath, outputPath, 9, 0, callback);
                } else {
                    CsoConverter.cso2iso(inputPath, outputPath, callback);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in convertFiles", e);
            mainHandler.post(() -> tvStatus.setText("转换出错: " + e.getMessage()));
        }

        final int successCount = completedCount[0];
        final int failCount = failedCount[0];
        Log.d(TAG, "convertFiles finished - success: " + successCount + ", failed: " + failCount);
        finishConversion(successCount, failCount);
    }

    private void finishConversion(int successCount, int failCount) {
        mainHandler.post(() -> {
            isConverting = false;
            progressBar.setProgress(100);
            tvProgress.setText("100%");

            String resultMsg = String.format("转换完成！成功 %d 个，失败 %d 个", successCount, failCount);
            tvStatus.setText(resultMsg);
            btnConvert.setEnabled(true);
            btnConvert.setText("🚀 开始转换");

            Toast.makeText(this, resultMsg, Toast.LENGTH_LONG).show();
        });
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}