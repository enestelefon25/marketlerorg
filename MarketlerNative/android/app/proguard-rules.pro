# kotlinx.serialization: @Serializable sınıfların üretilen serileştiricileri korunur (mock JSON okuma).
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class org.marketler.core.model.** { *** Companion; }
-keepclasseswithmembers class org.marketler.core.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class org.marketler.core.model.**$$serializer { *; }
# Mock JSON dosyaları (core/data kaynakları) R8 tarafından silinmez: Java kaynakları APK'ya olduğu gibi girer.
