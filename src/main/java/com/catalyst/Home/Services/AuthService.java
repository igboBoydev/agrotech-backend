package com.catalyst.Home.Services;

import auth.Auth;

public interface AuthService {
    Auth.GenerateTokenResponse generateToken(Auth.GenerateTokenRequest request);
    Auth.TokenResponse validateToken(Auth.TokenRequest request);
    Auth.ValidateEmailResponse validateEmail(Auth.ValidateEmailRequest request);
    Auth.ValidatePhoneResponse validatePhone(Auth.ValidatePhoneRequest request);
    Auth.ChangeFarmerPasswordResponse changeFarmerPassword(Auth.ChangeFarmerPasswordRequest request);
    Auth.FarmerLoginResponse farmerLogin(Auth.FarmerLoginRequest request);
}
