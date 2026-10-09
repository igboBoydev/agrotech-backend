package com.catalyst.Home.Utils;

/**
 * Ordinal order must stay in sync with {@code auth.ValidateType} in auth.proto
 * (PHONE=0, EMAIL=1, LOGIN=2) and with the token_validation.verification_type
 * check constraint (0-2) in the database.
 */
public enum ValidateType {
    PHONE,
    EMAIL,
    LOGIN
}
