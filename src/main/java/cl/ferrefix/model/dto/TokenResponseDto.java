package cl.ferrefix.model.dto;

public record TokenResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UsuarioResponseDto usuario) {
}
