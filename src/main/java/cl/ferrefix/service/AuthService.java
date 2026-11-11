package cl.ferrefix.service;

import cl.ferrefix.model.dto.*;

public interface AuthService {

    TokenResponseDto login(LoginRequestDto request);

    TokenResponseDto register(RegisterRequestDto request);

    TokenResponseDto refresh(RefreshRequestDto request);

    UsuarioResponseDto me(String email);
}
