package com.fnaf.moviedoom.repair;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Verified, one-purpose, forward-only APK delta. No network; input is unchanged. */
final class PatchEngine {
    private static final byte[] MAGIC = {'F','N','A','F','P','C','H','2'};
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024;
    private PatchEngine() { }

    static final class SourceSignature {
        final long size;
        final byte[] hash;
        SourceSignature(long size, byte[] hash) { this.size = size; this.hash = hash; }
    }

    /** Identify the installed APK before creating output; the source is read again when applying. */
    static SourceSignature identify(InputStream original) throws IOException {
        MessageDigest hash = digest();
        byte[] buf = new byte[64 * 1024];
        long size = 0;
        for (;;) {
            int n = original.read(buf);
            if (n < 0) break;
            if (n == 0) continue;
            size += n;
            if (size > MAX_FILE_SIZE) throw new IOException("Eski APK çok büyük.");
            hash.update(buf, 0, n);
        }
        if (size == 0) throw new IOException("Eski APK boş.");
        return new SourceSignature(size, hash.digest());
    }

    /** Compare a source signature to a PCH2 header without trusting a filename. */
    static boolean matchesSource(InputStream patch, SourceSignature source) throws IOException {
        DataInputStream in = new DataInputStream(patch);
        byte[] magic = new byte[8]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Onarım verisi tanınmıyor.");
        long oldSize = in.readLong(), newSize = in.readLong();
        byte[] expectedOld = new byte[32]; in.readFully(expectedOld);
        if (oldSize <= 0 || oldSize > MAX_FILE_SIZE || newSize <= 0 || newSize > MAX_FILE_SIZE)
            throw new IOException("Onarım verisi geçersiz.");
        return oldSize == source.size && MessageDigest.isEqual(expectedOld, source.hash);
    }

    static void apply(InputStream original, InputStream patch, OutputStream target) throws IOException {
        DataInputStream in = new DataInputStream(patch);
        byte[] magic = new byte[8]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("Onarım verisi tanınmıyor.");
        long oldSize = in.readLong(), newSize = in.readLong();
        byte[] expectedOld = new byte[32], expectedNew = new byte[32];
        in.readFully(expectedOld); in.readFully(expectedNew);
        int count = in.readInt();
        if (oldSize <= 0 || oldSize > MAX_FILE_SIZE || newSize <= 0 || newSize > MAX_FILE_SIZE || count < 1 || count > 16384)
            throw new IOException("Onarım verisi geçersiz.");
        MessageDigest oldHash = digest(), newHash = digest();
        byte[] buf = new byte[64 * 1024];
        long oldCursor = 0, newCursor = 0;
        for (int i = 0; i < count; i++) {
            int kind = in.readUnsignedByte();
            long offset = -1, length;
            if (kind == 0) { offset = in.readLong(); length = in.readLong(); }
            else if (kind == 1) { length = in.readLong(); }
            else throw new IOException("Onarım işlemi tanınmıyor.");
            if (length <= 0 || length > newSize - newCursor)
                throw new IOException("Onarım uzunluğu geçersiz.");
            if (kind == 0) {
                if (offset < oldCursor || offset > oldSize - length)
                    throw new IOException("Onarım kaynağı geçersiz.");
                discardExactly(original, oldHash, offset - oldCursor, buf);
                copyExactly(original, target, oldHash, newHash, length, buf);
                oldCursor = offset + length;
            } else {
                long left = length;
                while (left > 0) {
                    int take = (int)Math.min(left, buf.length);
                    in.readFully(buf, 0, take);
                    target.write(buf, 0, take); newHash.update(buf, 0, take); left -= take;
                }
            }
            newCursor += length;
        }
        if (newCursor != newSize) throw new IOException("Yeni APK eksik.");
        discardExactly(original, oldHash, oldSize - oldCursor, buf);
        if (original.read() != -1 || in.read() != -1)
            throw new IOException("APK veya onarım verisi beklenmedik uzunlukta.");
        if (!MessageDigest.isEqual(oldHash.digest(), expectedOld))
            throw new IOException("Eski Animasyon Testi APK'sı beklenen sürüm değil.");
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
