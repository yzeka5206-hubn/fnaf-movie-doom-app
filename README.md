# FNAF Movie Doom — çevrimdışı FPS60 güncelleyici

Bu dal **oyun değil**, daha önce kurulmuş `com.fnaf.moviedoom.optimized` oyunu
üzerine aynı imzalı v15 testini hazırlayan bağımsız küçük bir Android yardımcı
uygulamasıdır. Android 13+ gerektirir; internet izni içermez.

## Kullanım

1. Güncelleyiciyi yükleyip açın. En az 2 GB boş alan önerilir.
2. **YENİ APK'YI HAZIRLA** düğmesi kurulu oyunun APK'sını otomatik okur.
   Otomatik okuma başarısız olursa uygun eski APK dosyasını elle seçebilirsiniz.
3. Uygulamayı açık tutun; işlem tamamlanınca **Dosyalar → İndirilenler →
   FNAF_FPS60_Test.apk** dosyasını açıp oyunu **güncelleyin**. Oyunu silmeyin.
4. Bu yardımcı uygulama daha sonra kaldırılabilir. Diğer FNAF paketleri silinmez.

Kaynak APK, tüm dosyanın boyutu ve SHA-256 özetiyle tanınır. Yalnız aşağıdaki
iki gerçek sürüm desteklenir; farklı sürümlere yama uygulanmaz:

| Sürüm | APK SHA-256 |
| --- | --- |
| v13 Optimize | `f55c97179051b1ae5b36a255baa79d4c33e03c51ae0c15b4b0628f7a68ff9286` |
| v14 Dış Siyah | `becb43039e405b619dc39c8b2d00e5d0ffe991fac1a6bc6e2edba837371b8879` |
| v15 FPS60 hedefi | `3ae61eee3afd2fd1b55c73cefff22d5c540a000d4961d04e35ee0edbb48c1fe5` |

`repair-v13.dat`: `1040ce49eec462137c62579155c709e304fd6dbee4b152a49172189d53d56609`
`repair-v14.dat`: `002cb5668de506f9e64723a3792adbfa6c444c578715f71033d52d9197bf31bf`

PCH2 kaynağın ve üretilen APK'nın tam SHA-256'sını ve akış sınırlarını
kontrol eder. Hedef oyunun APK v2 imza sertifikası `7ffca05129f461b68852130392b01a10a041c2b785f5b4413bb944ac933569e6`;
mevcut optimize sürümlerininkiyle aynıdır. Android'in uygulama güncellemesini
reddettiği bir durumda oyun **kaldırılmamalıdır**.

## Otomatik doğrulama kapsamı

Derleme çalışması `42474432210`, Java patch testlerini ve dosya başlıklarını
kontrol ederek SHA-256'sı `98176561a92639ce81337d208e5dfc3b3feda51a02ca8137a1e2873d35f1d68d`
olan tek aday APK'yı üretip imzasını doğruladı. Android emülatörü kullanıcı
depolaması açılmadan başlayan v14 testini tekrar etmek için bu daldaki geçerli
workflow **aynı, hash'i sabitlenmiş adayı** alır ve Android 14'te her iki eski
sürümü yeniden dener. Gerçek eski APK kurulur, yardımcıdan yeni dosya hazırlanır,
tam SHA ve imza doğrulanır, `adb install -r` ile yerinde güncellenir ve eski
özel verideki sentinel kontrol edilir. Son artifact yalnız her iki test başarılıysa
yayınlanır. Gelecekte kaynaklardan yeniden derleme workflow şablonu
`scripts/build-fps60-updater-full.yml` dosyasındadır; yeni hedef için hash'ler
güncellenmelidir. Emülatör yardımcı uygulamayı test eder; oyunun ARM64 üzerinde
performansını veya Xiaomi 11T davranışını **ölçmez**.

Oyun, FPS sayacı ve optimizasyonlar için bkz. `experiment-fps60-counter` dalı.
60 FPS hedeftir; sahneye ve cihaza göre değişir, garanti değildir.
