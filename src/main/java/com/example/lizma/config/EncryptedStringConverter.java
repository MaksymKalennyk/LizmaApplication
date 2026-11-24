package com.example.lizma.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** Stores a String column encrypted with {@link TokenCipher}; reading still accepts old plaintext values. */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final TokenCipher cipher;

    public EncryptedStringConverter(TokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
