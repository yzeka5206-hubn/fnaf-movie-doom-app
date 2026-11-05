package com.fnaf.moviedoom.repair;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Verified, one-purpose APK delta. No network and no mutation of the input. */
final class PatchEngine {
    private static final byte[] MAGIC = {'F','N','A','F','P','C','H','1'};
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    private static final int MAX_SEGMENT = 16 * 1024 * 1024;
    private PatchEngine() { }

    static void apply(InputStream original, InputStream patch, OutputStream target) throws IOException {
        DataInputStream in = new DataInputStream(patch);
        byte[] magic = new byte[8]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Onarım verisi tanınmıyor.");
        long total = in.readLong();
        byte[] expectedOld = new byte[32]; byte[] expectedNew = new byte[32];
        in.readFully(expectedOld); in.readFully(expectedNew);
        int count = in.readInt();
        if (total <= 0 || total > MAX_FILE_SIZE || count < 1 || count > 4096)
            throw new IOException("Onarım verisi geçersiz.");
        MessageDigest oldHash = digest(), newHash = digest();
        byte[] buf = new byte[64 * 1024];
        long cursor = 0;
        for (int i = 0; i < count; i++) {
            long offset = in.readLong(); int length = in.readInt();
            if (offset < cursor || length <= 0 || length > MAX_SEGMENT || offset > total - length)
                throw new IOException("Onarım aralığı geçersiz.");
            copyExactly(original, target, oldHash, newHash, offset - cursor, buf);
            discardExactly(original, oldHash, length, buf);
            int left = length;
            while (left > 0) {
                int take = Math.min(left, buf.length);
                in.readFully(buf, 0, take);
                target.write(buf, 0, take); newHash.update(buf, 0, take);
                left -= take;
            }
            cursor = offset + length;
        }
        copyExactly(original, target, oldHash, newHash, total - cursor, buf);
        if (original.read() != -1 || in.read() != -1)
            throw new IOException("APK veya onarım verisi beklenmedik uzunlukta.");
        if (!MessageDigest.isEqual(oldHash.digest(), expectedOld))
            throw new IOException("Eski Menü Testi APK'sı beklenen sürüm değil.");
        if (!MessageDigest.isEqual(newHash.digest(), expectedNew))
            throw new IOException("Düzeltilmiş APK doğrulanamadı.");
        target.flush();
    }

    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 kullanılamıyor.", e); }
    }
    private static void copyExactly(InputStream src, OutputStream dst, MessageDigest oldHash,
                                    MessageDigest newHash, long length, byte[] buf) throws IOException {
        while (length > 0) {
            int n = src.read(buf, 0, (int)Math.min(length, buf.length));
            if (n <= 0) throw new IOException("Eski APK eksik veya okunamadı.");
            dst.write(buf, 0, n); oldHash.update(buf, 0, n); newHash.update(buf, 0, n); length -= n;
        }
    }
    private static void discardExactly(InputStream src, MessageDigest hash, long length, byte[] buf) throws IOException {
        while (length > 0) {
            int n = src.read(buf, 0, (int)Math.min(length, buf.length));
            if (n <= 0) throw new IOException("Eski APK eksik veya okunamadı.");
            hash.update(buf, 0, n); length -= n;
        }
    }
}
