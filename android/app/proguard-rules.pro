# Reguły R8 (zmniejszanie kodu w wersji release).
# kotlinx.serialization, OkHttp, Hilt i Room dostarczają własne reguły.

# Jsoup opcjonalnie odwołuje się do re2j, którego nie dołączamy.
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**
