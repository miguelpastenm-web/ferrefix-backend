package cl.ferrefix.model.dto;

/** url es relativa (/media/...): el front la prefija con VITE_API_URL. */
public record ImagenResponseDto(String url, String destino, String tipo, long tamano) {
}
