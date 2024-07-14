package com.devsss.onlyone.server.util;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GyUtils {

    public static boolean isNull(String b) {
        return b == null || b.isEmpty();
    }

    /**
     * 下载文件
     *
     * @param path        文件路径
     * @param downloadUrl 下载地址
     * @return 文件路径
     */
    public static String downloadFile(String path, String downloadUrl) throws URISyntaxException, IOException {
        URI url = new URI(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream inputStream = connection.getInputStream();
            byte[] buffer = new byte[1024];
            int length;
            File file = new File(path);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream outputStream = new FileOutputStream(path);
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            connection.disconnect();
            return path;
        } else {
            throw new RuntimeException("下载失败，响应码：" + responseCode);
        }
    }

    public static void unZip(String zipFilePath, String descDir) throws IOException {
        // 打开ZIP文件
        ZipFile zipFile = new ZipFile(zipFilePath);

        // 获取ZIP文件中的所有条目
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        // 遍历ZIP文件中的每个条目
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            // 如果条目是一个文件，而不是目录
            if (!entry.isDirectory()) {
                // 计算输出文件的路径
                String outputFilePath = descDir + "/" + entry.getName();

                // 创建输出文件的父目录（如果不存在）
                File outputFileParentDir = new File(outputFilePath).getParentFile();
                outputFileParentDir.mkdirs();

                // 从ZIP文件中读取文件内容
                InputStream inputStream = zipFile.getInputStream(entry);
                FileOutputStream outputStream = new FileOutputStream(outputFilePath);

                // 将输入流的内容写入输出流
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }

                // 关闭输入输出流
                inputStream.close();
                outputStream.close();
            }
        }
        // 关闭ZIP文件
        zipFile.close();
    }

    public static void deleteFile(String zipFile) {
        File file = new File(zipFile);
        if (file.exists()) {
            file.delete();
        }
    }

    public static String findFilePathBySuffix(String dir, String suffix) {
        File file = new File(dir);
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    String filePath = findFilePathBySuffix(f.getAbsolutePath(), suffix);
                    if (filePath != null) {
                        return filePath;
                    }
                } else if (f.getName().endsWith(suffix)) {
                    return f.getAbsolutePath();
                }
            }
        }
        return null;
    }
}
