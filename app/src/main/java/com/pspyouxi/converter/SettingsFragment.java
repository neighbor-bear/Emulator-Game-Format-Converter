package com.pspyouxi.converter;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {

    private MaterialButton btnUsageGuide;
    private MaterialButton btnAbout;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnUsageGuide = view.findViewById(R.id.btnUsageGuide);
        btnAbout = view.findViewById(R.id.btnAbout);

        btnUsageGuide.setOnClickListener(v -> showUsageGuideDialog());
        btnAbout.setOnClickListener(v -> showAboutDialog());
    }

    private void showUsageGuideDialog() {
        if (getContext() == null) return;

        String usageGuide = "📖 使用说明\n\n"
                + "1️⃣ 选择文件夹\n"
                + "点击「选择文件夹」按钮，浏览并选择包含游戏镜像文件的文件夹。\n\n"
                + "2️⃣ 选择镜像文件\n"
                + "选择文件夹后，会自动列出文件夹中的镜像文件，从下拉列表中选择要转换的文件。\n\n"
                + "3️⃣ 选择输出格式\n"
                + "• CSO（压缩ISO）：将ISO文件压缩为CSO格式，节省存储空间\n"
                + "• ISO（解压CSO）：将CSO文件解压为ISO格式，兼容性更好\n\n"
                + "4️⃣ 开始转换\n"
                + "点击「开始转换」按钮，等待转换完成即可。\n\n"
                + "⚠️ 注意事项\n"
                + "• 需要授予「所有文件访问权限」\n"
                + "• 转换过程中请勿退出应用\n"
                + "• 输出文件保存在与源文件相同的文件夹中\n"
                + "• 已存在的同名文件将自动跳过";

        new AlertDialog.Builder(getContext())
                .setTitle("使用说明")
                .setMessage(usageGuide)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showAboutDialog() {
        if (getContext() == null) return;

        String versionInfo = getVersionInfo();

        String aboutContent = "🎮 PSP游戏格式转换\n\n"
                + versionInfo + "\n\n"
                + "📝 更新内容\n"
                + "v1.2 更新：\n"
                + "• 优化UI布局，修复进度条显示问题\n"
                + "• 设置页新增「使用说明」和「关于」按钮\n"
                + "• 改善交互体验\n\n"
                + "v1.1 更新：\n"
                + "• 优化文件夹选择体验\n"
                + "• 新增镜像文件下拉选择\n"
                + "• 新增底部导航栏\n"
                + "• 新增设置页面\n\n"
                + "制作 By 邻家小熊 🐻";

        new AlertDialog.Builder(getContext())
                .setTitle("关于")
                .setMessage(aboutContent)
                .setPositiveButton("确定", null)
                .show();
    }

    private String getVersionInfo() {
        try {
            PackageInfo pInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;
            return "版本号: v" + versionName + " (" + versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "版本号: v1.2";
        }
    }
}