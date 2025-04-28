package dev.nexonm.distfs.metadata.service;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class HashGenerationService {

    @Value("${file.hash.algorithm:SHA-256}")
    private String hashAlgorithm;

    /**
     * Generates a hash for the provided file using the configured algorithm
     * @param file The file to hash
     * @return The generated hash as a hex string
     */
    public String generateFileHash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Error generating hash: {}", e.getMessage());
            throw new RuntimeException("Failed to generate file hash", e);
        }
    }

    /**
     * Generates a hash for a byte array (chunk) using the configured algorithm
     * @param data The byte array to hash
     * @return The generated hash as a hex string
     */
    public String generateChunkHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            digest.update(data);
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating chunk hash: {}", e.getMessage());
            throw new RuntimeException("Failed to generate chunk hash", e);
        }
    }

    /**
     * Converts a byte array to its hex string representation
     * @param bytes The byte array to convert
     * @return The hex string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
