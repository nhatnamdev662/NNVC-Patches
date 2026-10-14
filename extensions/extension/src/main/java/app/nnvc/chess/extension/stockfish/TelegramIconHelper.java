package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.widget.ImageView;

public class TelegramIconHelper {

    // Official Telegram Logo PNG (48x48 RGBA)
    private static final String TELEGRAM_LOGO_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAL/klEQVR42s1aW4xV13n+/rX2PvcDMwYM" +
            "xtwxxskAsgu4dkxKieo0UeS2bjyjNFJixUmTmFpy28e8DFO1qtQ2amQ1pnYj9aFKW88x6UuTh74gp05s" +
            "YohtKGBngGEACwzMDHM5l733Wv/Xh33OYcDDcMYZu11HW0faZ++z/sv335dgvqu/3/T27JNKn/jWrR2D" +
            "FzYHefObcG4XrN1I7zdCZAnIAgBApAZyVMSeBv1pSPCqV339jT9c9avWf/QO0laO7yMGBnQ+5Ejnj1J6" +
            "B2FahD944FerJCz8HognQOw0YaYkNgBVQZeA6gGyuYtAjIUEIcQY0Hto0piGyC9Bd4De/ugXX1x9oc1I" +
            "HxQQLhwD/TQYEAWAB14+szYbFp4h/NeCQnkJXQIf1UH1CoApuZD06wYBkOnv6TPGGpvNQ2wAV5seF5F/" +
            "0Sj+3i/61g3fvOevxUAqEfHbXzgc2uUr/lTEfMfmS12+Pg11zkEgQhqIyLygSJIiChLGBtYWSnD16jVA" +
            "/mY6Gv3uib4tcWvvD83A7v6DwSsDe9zOylAPwsIPgnzpIV+bAr1zAOy8iZ6DGUC8WBvYQhmuNn3I1Rvf" +
            "fPPLG4+2aJg3A72krYj4nS+d6ZNc7gUJMl2+Pu1kIQmfTSuAt/lSoHE0xST648N9G17affBg8Mqe2Zkw" +
            "t5J8RcRvrwx/W3KFl6js8rVpD0hAiJCpfS74lZpPkO6FsskV/33H4PDeV/bscbv7DwYdaaDF7fbB008H" +
            "xa7nfWNaqQoRMfgYF0kVY2BzReOqE3uP9G3cP5smZFaD/dfTT5hisaIu9vDOQIzg/2JRCRuoCTJWo2rv" +
            "kb6NL2OQFjMMW252lQ/82/AnTDbzupBl9QkFH6/kP8ADqMaGQpEpjeKH3vyj9Sdnulhp6kv6Afnhc0Ph" +
            "ohXBqyZf2uFrU16Msfh/sKjqbaFstT59uCG1R3p7e9xAajI0ANBbqZgBES0vD561pe4drjbtRIxth52P" +
            "+BICFgKDZvC++Xcx1tWmnS117wh94dkBEe2tVEyqgX4a7AMf+OHwGoT2mBhTpE9miaQLu5rhGiJA7Ima" +
            "I6wAxdC0M5CbzVpsSFKnbWi3HP6Duy9gH8T09qSxVAP+mSmUyt7Fmgpl4T9NrcMI4ZWYjD2uRR6LMwZ9" +
            "95TxmVUFxM2MZJa3xbtYbaG0yDWiP4cIe3uaUt5+4OxdLuZJsWYRnMNCByoRwECgJOqeiD1xR85i+7Ic" +
            "Pre2iEfuKmBZ3mJ4MsHjP7lw6+hKUoIAVD9hE/PJI19ZdzEAANfwX7TlrsVuanzBDFcAGEkxHTmi4T3y" +
            "gcHWJVk8urqIR9cUsa4ctp/3BC7VHJwnQivg7JIQTRIflLu7fO3a4wCeD9BPQzndS5cQENwCgB0v08R2" +
            "pETdEYEA6xdnsOfuIn5ndRH3L8vBSkugQAswgQjOTyWoJoo7rIW/JR0CuoSq6EN//z8Gn9x4agNhtvuo" +
            "LgDMh6HfSIo6r8RUovAEVhYDfGFdAb+7pogHl+dRCq+Hk2rkUI89lpSzsAA8U45OjkUQSXO7OegwPqoL" +
            "iZ1b7/nSpiA0skvCbFGjOueDfYGkEAFQd4rIE4syBp9eWcDn15bw26uKWJa3MwJSKvGL4zVM1GKsv7MM" +
            "ad5v/c+712JYQdPg59jaO9psoaARdgUUfArGQikqgO1M2kDsFfWm67u3O4vPrinic2vL2NydmYFrthmt" +
            "RQ6nL03BqaJnVReyoW1zJgJcrnuMTMbIGEAVc7JAiooYS+GDAYlNUAVA4RzlgRUBSVQTIlFiZTHA59cV" +
            "8Nj6Mh5ekUemCWwCUE2VaUXglLgwWsX50RpK2QDb1nQjsCYty1rlGYBT12JcrjkUQgPl7ctbUkHFpoDK" +
            "9UhigJwTPpOxR2gEv7Esh8fWL8Kja4tYXgg+IG0BYJu53+hUhPOjVUzWYiwt57D57sWwRtpEN3MdAIK3" +
            "r9QRe6IY4PaOhBQmMQCuD5RcKupTb81Zcm0BlMBn15bwlfu68PBdBbRyU+V1DBuRNlGNxOPclSpGpxvw" +
            "nljelcemFYtgmi/KTbYEAP9zNUo1MrcBt/1p2jTA0gBAfi6dOQUWZwz+7tMr2p5ECSgJI9JmpkXUxfEa" +
            "LozV4JyCAFZ0F7BxeXnWxKTFfN0pTo5FyFrpAD5oS49EPgBuzTGRWvVE5PClH5/H7tVFfGZ1EduW5hDe" +
            "VCJMNxKMXKlioha3IbSyu4B1d5bmQEJqwKcmYlyqJghaLrTTUEkiIFEXI8VbcaFI4XFyLMJbVxr4p2Nj" +
            "uLc7i10rC9i9qojty3J4/1oN743XQSUCK3CeWLO0iFVLijfgfZZcH4Dg+NUIk7GiO2ehnQaiFNr1gMRV" +
            "EVukT24ZBwggHwgKQarik6MR3r5cxz+8NYavbyri8dVZkIAxaTDbsLyMFV35OYmfif8j79fT4rwj/Ldz" +
            "IhEkVw3IYdjg+tu3uFTTDJIkchbozlrkAuCn7zfQUMCa1C42r1yMFV15KHnbppNpptLHRxsIm+93VP2D" +
            "hA1A5bAhMAQxIIWd1iBKIFEiYwTnq4p3JhLkrIFTYujiGOqRgxFBqxV3CxsEAIxMxjg7ESPTNOCOaKAw" +
            "rXTNkBHIz+gdSDXzbYMI04h86EoMg1SCQ++P47+OncXI1cl20TKbZ2ndevtKA5ORb1djnV1q6D0IHDKJ" +
            "T36mUb0m1grYsQwAEEoiYwVvjiUYixUGRMZa1GKH1999D68NXcRE5GEkZY6zsPDLy/UZhtvBvlSKsaJx" +
            "vSbWvmpOX+g5Q+AwwjwJ0floQAmEAlysehwbdwiFiJXIWoEYg784PI4v/McIfnqh2g50LRHZZvZ69Eod" +
            "GWtSBjvaVxSZPKl8452vbhgyGBClsiLGCltZ1Hy4aGri0JUIsScyAkQe+MEIcaJq8d5UhCd/cg5/fegy" +
            "EiWMAE4JJXB+KsHwtTg1YO2odQdSIcYKlJV2V8IqDrjqxARsYEgl59FUUAWyFnhrNMalWoyEwIvDwDuT" +
            "QF5SbWSs4HtHruDL/3kO5yZjhCaN4CdGGxhruDSAdWS8StjAuOrEtSjMHwAAi0Haq0/eObnksWeWm8Ki" +
            "hzVp+Pk2swwEdU+MTCteGxOcrREF24RLE96l0GJoPMKPz0xidTmD0Ar+/vBVnJ9KkDGmI/9PUm1hsfFR" +
            "tH/4G5sOYJC23VbZ8OLx1dbYY7S2BJfIfAv7tIxMu8UZ0/Q8cqPNWiNoNHOkUmgwnSiyNnW3tw0aJGFD" +
            "gjpFk2w9/bUtaVsFA6KoVMyZb20556l/abJFQ6Yl6XwNOitAIGmB/gFTQor9jBVkraDh2E7eOjQ7b3JF" +
            "oy75q9NPbT2PSsVgQLRVXgsIuee5oVCzyX/bfGmnr095kY+utSjz6B+Q6m2+bLU+/Ub2Du46cbzHYSDt" +
            "6TWxLsQ+4NSz90Ze7Fc1aUxKEBpS9aNocLHpuTr6UFWC0KiLJozXJ0/0bYnbNN8w4BgQxeCgHfnWfe9o" +
            "XH8KYgCxaRL0MfVIZ3FxhFhCrCRR7etDf7L1JAYH7czh343epq/Po/9gMLz3/gM+rj8t2YKFsSSp8w0P" +
            "v96kJvU4MJaSLViX1J4+t/f+A+g/GKCvz99+RtZ/MMDAHrf2+0e/bTK5/aCCLvEiYj+m6YyXILQwFho3" +
            "9o7s3bq/RVPnU8rmJGT980d7YTMvSpjt0sb09ekkF9qqW9NKeJMrBprEk+rjb5zbu61y81TmtkO+FE7S" +
            "hNO2irrkUxpHPzeFxQGMFSpdOlBcuOEklQ7GiikuDjSJXjP1xiPn9m6rpLC59az49sGqxf03XwjXPPBb" +
            "zxrwO5IrdrNRhap3acZMM/95QrMyISg2sCZXBKPaOMm/Hbmk38XAlnguyX/oowYbv39stbOZZ0j/lM2V" +
            "ltI7MG4dNRACFLSOGsjMzJkz5jEUMdZIJg+xFtqojtPYfzaJe+7sMz0jC3rU4IZnB2laElm1/827rQa/" +
            "r+QTIuZBCTJFGAtQAZ8gzWxnHvYwgA0BMYB6MImrII/A8GU12R9deHrze9c1vtCHPW46boOefTJTtWuf" +
            "O3ofbPgwmTxE4B4oNgBcCpF8Ey11QK7C4IwAp0TM6yL252f3fuLdG6D6IY7b/C8qxZ7xkXNhtgAAAABJ" +
            "RU5ErkJggg==";

    private static Bitmap cachedBitmap = null;

    public static synchronized Bitmap getTelegramBitmap() {
        if (cachedBitmap == null || cachedBitmap.isRecycled()) {
            try {
                byte[] decoded = Base64.decode(TELEGRAM_LOGO_BASE64, Base64.DEFAULT);
                cachedBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            } catch (Throwable ignored) {}
        }
        return cachedBitmap;
    }

    public static Drawable getTelegramDrawable(Context context) {
        Bitmap bmp = getTelegramBitmap();
        if (bmp != null) {
            return new BitmapDrawable(context.getResources(), bmp);
        }
        return null;
    }

    public static ImageView createTelegramLogoView(Context context, int sizePx) {
        ImageView iv = new ImageView(context);
        Bitmap bmp = getTelegramBitmap();
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(sizePx, sizePx);
        iv.setLayoutParams(lp);
        return iv;
    }
}
