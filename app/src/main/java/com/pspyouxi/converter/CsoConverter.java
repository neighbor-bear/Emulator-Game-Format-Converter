package com.pspyouxi.converter;

import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * CSO/ISO format converter - ported from csochef.py
 * Supports ISO->CSO compression and CSO->ISO decompression
 */
public class CsoConverter {

    private static final String TAG = "CsoConverter";
    public static final int BLOCK_SIZE = 2048;
    public static final int CISO_MAGIC = 0x4F534943; // 'CISO' as integer
    public static final int HEADER_SIZE = 0x18;

    public interface ProgressCallback {
        void onProgress(int current, int total, String fileName);
        void onComplete(String outputPath, long outputSize);
        void onError(String message);
    }

    /**
     * Compress ISO to CSO format
     */
    public static void iso2cso(String isoPath, String csoPath, int level, int align, ProgressCallback callback) {
        File isoFile = new File(isoPath);
        if (!isoFile.exists()) {
            Log.e(TAG, "ISO file not found: " + isoPath);
            if (callback != null) callback.onError("ISO文件不存在: " + isoPath);
            return;
        }

        long alignBytes = 1L << align;
        long alignMask = alignBytes - 1;
        long isoSize = isoFile.length();
        long totalBlocks = (isoSize + BLOCK_SIZE - 1) / BLOCK_SIZE;

        Log.d(TAG, "iso2cso - input: " + isoPath + " (" + isoSize + " bytes), output: " + csoPath + ", level: " + level + ", align: " + align + ", totalBlocks: " + totalBlocks);

        RandomAccessFile iso = null;
        RandomAccessFile cso = null;
        Deflater deflater = null;

        try {
            iso = new RandomAccessFile(isoFile, "r");
            cso = new RandomAccessFile(csoPath, "rw");
            deflater = new Deflater(level, true); // nowrap=true for raw DEFLATE

            // Write placeholder header + offset table
            byte[] headerPlaceholder = new byte[HEADER_SIZE];
            cso.write(headerPlaceholder);
            byte[] offsetPlaceholder = new byte[(int)((totalBlocks + 1) * 4)];
            cso.write(offsetPlaceholder);

            long[] offsets = new long[(int)(totalBlocks + 1)];
            long currentOffset = HEADER_SIZE + (totalBlocks + 1) * 4;

            byte[] blockData = new byte[BLOCK_SIZE];

            for (int block = 0; block < totalBlocks; block++) {
                iso.readFully(blockData);

                // Compress
                deflater.reset();
                deflater.setInput(blockData);
                deflater.finish();

                byte[] compressedBuf = new byte[BLOCK_SIZE + 64];
                int compressedLen = deflater.deflate(compressedBuf);

                // Apply alignment padding if needed
                long pad = currentOffset & alignMask;
                if (pad != 0) {
                    int padding = (int)(alignBytes - pad);
                    byte[] padBytes = new byte[padding];
                    cso.write(padBytes);
                    currentOffset += padding;
                }

                // Store offset and write data
                if (compressedLen > 0 && compressedLen < BLOCK_SIZE) {
                    offsets[block] = currentOffset >>> align;
                    cso.write(compressedBuf, 0, compressedLen);
                    currentOffset += compressedLen;
                } else {
                    offsets[block] = (currentOffset >>> align) | 0x80000000L;
                    cso.write(blockData);
                    currentOffset += BLOCK_SIZE;
                }

                if (callback != null) {
                    callback.onProgress(block + 1, (int)totalBlocks, isoFile.getName());
                }
            }

            // Final offset
            offsets[(int)totalBlocks] = currentOffset >>> align;

            // Write CSO header
            cso.seek(0);
            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_SIZE);
            headerBuf.order(ByteOrder.LITTLE_ENDIAN);
            headerBuf.putInt(CISO_MAGIC);
            headerBuf.putInt(HEADER_SIZE);
            headerBuf.putLong(isoSize);
            headerBuf.putInt(BLOCK_SIZE);
            headerBuf.put((byte)1); // version
            headerBuf.put((byte)align);
            headerBuf.put(new byte[2]); // padding
            cso.write(headerBuf.array());

            // Write offset table
            cso.seek(HEADER_SIZE);
            ByteBuffer offsetBuf = ByteBuffer.allocate((int)((totalBlocks + 1) * 4));
            offsetBuf.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i <= totalBlocks; i++) {
                offsetBuf.putInt((int)(offsets[i] & 0xFFFFFFFFL));
            }
            cso.write(offsetBuf.array());

            if (callback != null) {
                callback.onComplete(csoPath, new File(csoPath).length());
            }

        } catch (Exception e) {
            Log.e(TAG, "iso2cso failed", e);
            // Clean up partial output file
            new File(csoPath).delete();
            if (callback != null) {
                callback.onError("压缩失败: " + e.getMessage());
            }
        } finally {
            try { if (iso != null) iso.close(); } catch (IOException ignored) {}
            try { if (cso != null) cso.close(); } catch (IOException ignored) {}
            if (deflater != null) deflater.end();
        }
    }

    /**
     * Decompress CSO to ISO format
     */
    public static void cso2iso(String csoPath, String isoPath, ProgressCallback callback) {
        File csoFile = new File(csoPath);
        if (!csoFile.exists()) {
            Log.e(TAG, "CSO file not found: " + csoPath);
            if (callback != null) callback.onError("CSO文件不存在: " + csoPath);
            return;
        }

        RandomAccessFile cso = null;
        RandomAccessFile iso = null;

        try {
            cso = new RandomAccessFile(csoFile, "r");
            iso = new RandomAccessFile(isoPath, "rw");

            // Read header
            byte[] headerBytes = new byte[HEADER_SIZE];
            cso.readFully(headerBytes);
            ByteBuffer headerBuf = ByteBuffer.wrap(headerBytes);
            headerBuf.order(ByteOrder.LITTLE_ENDIAN);

            int magic = headerBuf.getInt();
            /* int headerSize = */ headerBuf.getInt();
            long isoSize = headerBuf.getLong();
            int blockSize = headerBuf.getInt();
            /* byte version = */ headerBuf.get();
            byte align = headerBuf.get();

            Log.d(TAG, "cso2iso - magic: 0x" + Integer.toHexString(magic) + ", isoSize: " + isoSize + ", blockSize: " + blockSize + ", align: " + align);

            if (magic != CISO_MAGIC) {
                Log.e(TAG, "Invalid CSO magic: 0x" + Integer.toHexString(magic));
                if (callback != null) callback.onError("不是有效的CSO文件");
                return;
            }

            long totalBlocks = (isoSize + blockSize - 1) / blockSize;
            Log.d(TAG, "cso2iso - totalBlocks: " + totalBlocks + ", csoSize: " + csoFile.length());

            // Read offset table
            int[] offsets = new int[(int)(totalBlocks + 1)];
            byte[] offsetBytes = new byte[(int)((totalBlocks + 1) * 4)];
            cso.readFully(offsetBytes);
            ByteBuffer offsetBuf = ByteBuffer.wrap(offsetBytes);
            offsetBuf.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i <= totalBlocks; i++) {
                offsets[i] = offsetBuf.getInt();
            }

            // Decompress blocks
            for (int i = 0; i < totalBlocks; i++) {
                int current = offsets[i];
                int nextOff = offsets[i + 1];

                boolean isRaw = (current & 0x80000000) != 0;
                current &= 0x7FFFFFFF;
                nextOff &= 0x7FFFFFFF;

                long start = (long)current << align;
                long end = (long)nextOff << align;
                int dataSize = (int)(end - start);

                cso.seek(start);
                byte[] data = new byte[dataSize];
                cso.readFully(data);

                byte[] block;
                if (isRaw) {
                    block = data;
                } else {
                    Inflater inflater = new Inflater(true); // nowrap=true for raw DEFLATE
                    try {
                        inflater.setInput(data);
                        block = new byte[blockSize];
                        inflater.inflate(block);
                    } finally {
                        inflater.end();
                    }
                }

                // Write block (pad to blockSize if needed)
                byte[] writeBlock = new byte[blockSize];
                System.arraycopy(block, 0, writeBlock, 0, Math.min(block.length, blockSize));
                iso.write(writeBlock);

                if (callback != null) {
                    callback.onProgress(i + 1, (int)totalBlocks, csoFile.getName());
                }
            }

            // Truncate to correct ISO size
            iso.setLength(isoSize);

            if (callback != null) {
                callback.onComplete(isoPath, isoSize);
            }

        } catch (Exception e) {
            Log.e(TAG, "cso2iso failed", e);
            // Clean up partial output file
            new File(isoPath).delete();
            if (callback != null) {
                callback.onError("解压失败: " + e.getMessage());
            }
        } finally {
            try { if (cso != null) cso.close(); } catch (IOException ignored) {}
            try { if (iso != null) iso.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Check if a file is a valid CSO file by reading the magic number
     */
    public static boolean isCsoFile(String path) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(path, "r");
            int magic = raf.readInt();
            return magic == CISO_MAGIC;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (raf != null) raf.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Check if a file has .iso extension
     */
    public static boolean isIsoFile(String path) {
        return path.toLowerCase().endsWith(".iso");
    }
}