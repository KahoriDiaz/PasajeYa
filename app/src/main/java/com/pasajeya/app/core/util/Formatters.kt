
fun formatKm(meters: Double): String =
    String.format(java.util.Locale("es", "PE"), "%.2f km", meters / 1000.0)
