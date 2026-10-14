package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.widget.ImageView;

public class NNVCLogoHelper {

    // Official NNVC Knight Logo PNG (128x128 RGBA)
    private static final String NNVC_LOGO_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAQAElEQVR4nOxdCYAUxdX+anZ2YQHZ5UYE" +
            "BEHiFY1HuAQBr6jxIh6JJr/RHF5R4/2rgD/RKBqjeN8imogHnsTEHCaKV4zgrbgIKCogIAvsfc10/dU9" +
            "091Vr6p7emZnZlezL5Ger7u6uurV+95X1d0zG0eBbOKtbfvFSqz9Lc63Y2CDLI6BjGEQ53wgA3qILZjY" +
            "wcUBiB0Uix1ia8aMpaBzHsV2PX5xWNJxlj5uF0xdz8epAu75qeOZsFM/wWr99taS6jdj/3zWKNq7Ueze" +
            "II5uYIxvFPvXgLN/Lbt8yMsogDHkyY57jJesrU5MYZz/QPTuaFH1dmLrHPOcHIIdJ6SxvwnH3h4Jp+rT" +
            "MTPWl/7XhJn7kUv1p6+pNJ9iSMHgnsK9MScNSlfBtfMNeC2z8JQF64mqXYa+jONZEnmwdgfA1Lm8Mtk9" +
            "cbkF/lNRWV+uMVVnXBjzfea4TDbUZzpOmB/E9HwxP2r95uuFtV/Hrj/g480ic8wr7WZd/f6l229BOyzn" +
            "AJhwAy+PlSd+Ldr2v6JRlenWKWW4EcvMD2JmZuarl4uGo9ef3huKzf3TmC9XSzMBbQdlvqGdDMr5W8T1" +
            "rq3bipvXzB3WhBws6wCYOpvH2wa1/Vyw4XJx9hCNmZbKdMuKxvycNZ9qvMVlouWd+fnUfMMcIF1/IPOD" +
            "8DqL4TfbYei8F2ezBLKwrAJg31v4kFg8sVBE4kR3n6dhGTQ+XPPTTiy45lOc/ldqHicYHaf5pH5/8P1g" +
            "8i6QahDHa6yNHVk1Z2g1IlosasH97mjdk8UTS1KDz0jn1MiEIVIREslOeTkYGLx0J2MYsMt01wke8yUM" +
            "CUPCTPYdl6JCwl79gVjOBCCZQ/ePj0H8BeIvhPuLE/I4Wz7RKk0uHT1r9Z6IaJEywOQ7E0cJhy0QV+nh" +
            "+si/uG9fZ83XZ/tB/VFxFOabMoFXX3aaD3+wTdirtzHG2InLrxj+DDJYeAbgnE26s22maMhTzuCnI1WN" +
            "bEjOMEWymSmQmEKxiZkqkwFV42HMBMHMVzHk64G0l2DAnMmCmW7OBEZNB2E+SKaAmfle/d556CHmXk/t" +
            "OPPzGchgoRlg0l2J40SNj8lM59LFZWdkp/nuJhMz/6vW+QhK+1pwwRAM6QapXeH2pPJHq64a+SgCLDAD" +
            "TLy79Tuiggf8SIOxMTQyo2k+oGk8L4TmgzBfwlw/rtTPg3Cw5qv+gRHnU/MD5xjw2yOId3/YnMCYASbf" +
            "zAfw0rY3xcdhro/kyn1owl8PzfeYHYjN/SvyOl++DDTmS9elzJeqtfEXsBJ7r5yz41cgpmUA+wYPL0s8" +
            "BXvwO4Pm8wzMNDI/s+bzAOZnrfmgzJc12aT5kr8QRfMBkPqo5sOA3XJpPIzHSp7c+9SlpSCmBYB9d0+c" +
            "ta/fKb8x4Jlve/qDyqSIV4+nOi2XlzpJsMw4uTwjnadY2kBjnoRZUL3edcNvIpETST9VPyiDQmSEBhej" +
            "mi/LAtxolZiexkzCXMKiHZNqBvS7AMQUCTjwLl7RZLV9JgpXKJ0md/hMd/yi3Nv3B5kRZ4RhZAy6fNzh" +
            "i1q/jIPu6GXqXxZ3+HLwV0h9jG1NlmLk6tkjt7pjrmSAZitxnqikgoNr6ZoFYkYYxAjD5M74jHL75mWW" +
            "QKwyRd6CBzGfloOGmaEdJswYaRfJDDD4AYTpPjNVvzryJPsVmZgPI9NNzIcBiw+VsVbrXEjmZYBx9/BB" +
            "ZcnEcrF+rAhjesHv7XOV+V+n5/kUp+7tq7hozIcqk5L/tlrdYl4W8DJAaaLtUnGwAsSpuWk+CCMoYyB1" +
            "UsWU+UyRWG4sBy4zF+S6lPmAmrFA2hUeXORE6MwPD76gwQ5lvj963uBmYr4y+JDJg8pYs3VJ+kKpANjl" +
            "MV4mjp0CKVTUNKpHKnhwJDudRdc6X/UXQvxFmA9G/EWPk2AC6Y9ynJLHqf508YF5AdC3OjFR7OytRiSg" +
            "apOJ+eGaz43ML47msxCstAsm5ge3E0TDfeYDaiZARM0Homg+xUHMV4IVIPV7xytGXLxyHNwAEEJ8CAIi" +
            "V49kM1NgZIocoSBpUMdGZnat86Xj0JhP074SLCCDL+MYjk5tUoWPbp/mq8e71vmcBHeWmg8emflMGnwa" +
            "DIGDbx/nzAkANuGulp1iFvuoa52v46/dOt/gzzB/IWbtHGcWGw+up/NcNF9hPnyGgRde8yn+r1znA4Y5" +
            "hdoeKOPMxsfEQnWE67yu5/nBzKTHjUx02yVhNcjpbD5Y801bpRyI5ivX8c2EWepMu4YRcdGHEWHp0JQ+" +
            "qMZnpfmksUyRWG4sZ+ykd135PF3jKc4kK+RE6MwPlx2/X1lovs8G92hG5sv+4oA+24eO/fq9dg2OCTBY" +
            "TY8m5net89XgM2VKhPgrA/Pzvc6HxHS53R7zPTw4bv8D5Kb53Mj84mg+C8FKu2BifnA7YdBMpV1Zaz6C" +
            "me9cNjvmgzBdY76h/QpW/TwiZv/jOrdrnU812aT58DGiaD4AUp/K6OBg19K8XA5Qzw/BvuZL7UrVJ+YA" +
            "9tM/TplPNV89npXmS1jNLIjAfPU8GDALqldiuhrMlOnKiaSfJs2Hgj0nZ6P54JGZz6TBp8GQafAhXw+S" +
            "v3zSVMTMzDdomEHTGcEwYG5gerDmU+ZDyyyImlmU/oD0BwjV4EJrPrLRfJIJFPLoGShc86Fljng2mq8w" +
            "Hz7DfAabMHE+EIH5tBw03LXO9xwBNbMQDNJ+gmNBTAHnhHmUOdCYaGK+xtQcNV+fA3St84NwiOZrOO4P" +
            "arDGZ6X5pLFd63y3fUwZ3OKs8zXN13DMFOk+E2FgPifM5EbmI5LmgzBfwlw/rtSfPt66dR2SzbUIymR0" +
            "8BpXvYSNf78CjZ+9AblCNfi+Ket8qP3y3elVzCbc0sSVwQAZHAMG1Mqj4uj1p/eGYuCrF25A3QdPO597" +
            "jJ6GflMvQLx7Lz2zpOGW1+9D7Vt/9M4fcMR1KB+6l+pEFt5OlrGfzMhMTrDcriDsw+xw5vrdDU9lAK+Q" +
            "wjweTfO5mZmmTJCN5vMA5ru4ranGG3zbGle+gPULT0dr7XrIJ9jlrbYWbPzrbGXwbav5z32GtO479Ruw" +
            "ztewX3/q+jGzFgKRNV/CRm12y8HkDPU8ExMDNT/ZBmptNWuwYdHFsFobFc3f8urtaPpksVOmovc2OOUn" +
            "xzufWzdWwRKBxEmwU9nIWvPBI2u+i03BUAjNd8u7wx7jElPkszr7Oj9W1t3zwy9O+hGm7Dve+ZzY+jk2" +
            "v3aH59yWDVWoX7bIOTZk20H456IFOPLQA71zWzevzr/mo/Os802ZRb5e/Ou6zo+V9nT7jpbWVjw87xZM" +
            "PGg6Vn++BvUf/gmxbtuAxUrRkGa+bXfOvRpjRu+AzVu870Wged27iPfeDvFe/fFNXOcr/gMZR4HjfrpN" +
            "d6aD3tuHUr9/fth7+9222xMta9/G4ldeR6+ePZwBPuSYk5xO1761ALIdP/1wTJ2UyhJlZWXe/tqlD6Du" +
            "zQfBynqhtP9olPYZgdK+I1A2+Nso6zMcYTKgpPEIOOJ7+0q7gzRfuf8hDa5FsD8HULHLupKhh82YDSlC" +
            "pGNeBMpYZj5A1vmaFvnVgGK5gNRHr35lDgBtbuJcN5lA0+rXUFNbh9133RkHTNkXn3+xDu8vq4Js391r" +
            "d9x90xz0KC938LaDB+LpP//dOS+ZTKZSfrIVybr1aPuqCs2fv44GIRvNX36AWHkFSnsPCWW+NHpSR8yZ" +
            "QOm+jCMMPjSccbjI9TwP+80ed1M9V5mfKZLDmZ4t8004av3VL9+KuveecDqz5IVF+NaOo5CNVa2pxWfL" +
            "38G7HywT/32Ed99fhs/XrNPKlVRuj167TUf56AMQKyltH/MJ0zXmGzKhginzDccD20WO2+0SAdDAO+s6" +
            "35j+0jHFrSS+mH+MmMVvxQ4jhuOdV55Dtrap3kL/XuoXpO2s8PqSt/HgI0/gT889rxwr6TUIldMuQdnA" +
            "nWlDSdd8ZodhuV/Z4Mz1uxs9Y4PgmBshcuR5hQyRCorhMhMhs30VK/WDMEGOcCY3WmXS1jcecAbftmOO" +
            "PBS5WL+eTNtnLxO/d8B+eOiem7B86Qu44Oxfon+/vs6xZP0GbHr2AtSJ+wm2/Ljt8jsk+6tzrPMV2TJg" +
            "NtaWACkiuNy6EOZTzYcRp//1WpsuI2GP+YFYZb5tjZ+8go3PzXQ+D+jfz0n/fftUolDW2tqGp579G+6Z" +
            "vwBvvPWus6+0/xhU7n+ZkxU8C2GmKRiiMJ0kSUheDsU+VzltHpRxHHtjHf+6aL6NWzatwpeP/0rQsdnp" +
            "xN+e/AMmjN0LxbKHH38GZ5w/U6x+LLB4ObYZfzp6jDk4RPPD5gAR/EU134CDNB6GOQFIu2JuI5RZIle1" +
            "gxvSm9sbRrC5HDSc6/P8Tc/P8Qb/isvOL+rg23bCsUdh0SP3omePcvBEE2pfmYv695+APNuXmd7R63w6" +
            "rkp5Lj0LCNRkErnIUfP1OQDPSvPtC9Z+8Azaqlc60L7zd+6ZP/fK/vT087VJW6Fsv4nj8MKzjzrLSdvq" +
            "l9yH5jVLNY1XMCUBgnEhNd/NVG4LY1JgGJmvrPM15vvngZwHouHuNZl0nBtwkKxYrfXiad49TjXxeBw3" +
            "/2626yPces8DjkY3NOb0g9k52U5jRuEvCx9wMoHdsZoXrkbbls/cbkLTfJZ58KExHxrTdebDyHyEYDmT" +
            "xDwNQjDTg+/tgzBfwlw/rtTPgzAnmSfVvupXxf391gZn39mnnYyR2zu/YOcs267+/W3OLd7jp38fxbRR" +
            "I4dj3m3XpdqZaMaWv89CUjxcUgZfI096w6VMCSnYIWNomUNhPjMFm+pHhGD7xFihNJ+FYJnpFFPNt89r" +
            "+WolGj76i1N24IB+uOTcMzxH/eHRJwXzG3Hr769ALBb5t6/zZoceNA3niIC0zWrchK3P/x+sRKt3XGN+" +
            "tprPCAbxFxCo+UH+ludgMc4zMDNHzecBzM9G892nlNUv3uD573dXXIbycv9J4LAh2+LeW67F+H0i/0B2" +
            "3u23sy7CxHF7O58T1StQ/+YD8NWRK2U7WvPVYBNzAHl9bdTm9EXNzFfPgwHnqvkuMxpWLhbP7T9y6h3/" +
            "3b3wgyMOgWxHff9g50FPe2zL1hrcfNd8fPjRx8jV5t9+vXcvoumjp8VNo40ZBx8a83PR/PSJUDOBjOXM" +
            "os0BstN8ynwgUPMzZRauZgKq+faWt7Vgi9B+166/KuOPX+dks666HjOvvA5nXXQ5crXBgwZg1sXneLjh" +
            "bfvtI1XjC/E8XymvZVQVQxln7s4BiPMRhfm0HDScj/f2a997HMmGjc45J//4OHx7l51QCFux6lNnu/Gr" +
            "TWiPnXzisRgxfKjzueWTF5Co+SJ7zTdgmclhmo8Af9PxAOchc4AcNV+fA/DcND+Nk+I+f81bDzmfu3fv" +
            "hlkXnY1C2ccrUwFQWVGB9lhJSYmUBTiaPnii02m+l2HT4xjrLOt8EGwPPm9LresvPPtU555/IezRchGg" +
            "WQAAEABJREFUJ59F9ebUX147YMpEtNeOPeowDBrY3/nc/OliZ3nomcb8XDRf13hkoflqZpbmAB29zpeD" +
            "L1G/CXXvPu5A25lnp5dZ+bb6hkb89rqbPfzj449Ge83Oaj//nx+mgNUmpOAl52Og5hMSKcz3E60mG+aM" +
            "qmIYMM3gyhwgG81nIZhqkM78FFYzjZ8Jtiyd7zlqxgVnoby7v+zLp/3sVxfhsy/WOp/HjB6Z9QslQXby" +
            "icel3jIS1rz6ZXTkOt+ElQyAHDWfBzA/l3W+jFu3rEFj+qbPjjuMcCZ/hTB71v/X51/0Lj154jjky+wV" +
            "wcH7T3Y+Jza+L+5gNnrHOlrzQecA6VoCmC81mmp+GudL893Qrk1P/GyTl1X5tKuvv81Z97tmt62yYhvk" +
            "06a79yu4hdZ1b6Y/56L56ROhZgIZZ6P5amZJzwGCmQ8Eaj5XmZ+L5jPpBO5o/wY0LP+bU27nb43G0Yd/" +
            "D/m2OTfcjmvm3u5ht10lsRLk0w6aOsn73LpmiaLx8nV15lPNl8prGVXF8oAFab6L3XGPRdV8igvx/fya" +
            "tx+Da5eedybyafYLHKf++lIRALd5+7qNmORptb2Ey6fZqxb3vkXb2iWiAUl0xDo/BYPG1c4AETVfnwO0" +
            "b50vY/s0q7UJDVUp7d9u28HOLd58WWNTE475n9PxyBOLvH3dtt8X3XeY5mH7IVO+7cBpqSzA2xrFo+JP" +
            "O0TzuVI/HVdpDiBtzJqfvma+Nd8t37DiH2LN3OJc5tSTT/CY2V6z7+4dcMSJ+OfiV7195Tsdjsppl4r7" +
            "9eu9fbvutCPybfuO28f7nKxeFVHzdY1vj+brr43Bz0DceR8A0TRfnrjkqPnyCX5gp3DDx/7bPCedcAzy" +
            "Ya/8ewnGHzgdH1b5D3l6TzgLFRN/Ja4ZQ7LWD4Ddd9sZ+baxe+/hfW6rTrVBYb5PSMLkoIyqYhhwJs2n" +
            "93uMzwIUphsw1SCd+YCn+VCxHAzuOjjZ0oDW9R84++zU369vH+ez/YrXmL2n5nSP/rqb78Jhx52MTdWb" +
            "Hcy6V6LPIdegx87f99rVtvFD59iuO4/xvjWUT6us6I2h4nG1bcnNq9AR63x5KzPfxXFFK5iUNqRM4GJ7" +
            "cAvxN3aStV96TpNZUxIvwWhxLyAbswf85DMvxEuv/sfbVzpgJ5HyZ6KkVz/P6QnxuDax+RPn+KEHTUWh" +
            "bM89dsWadV/CqvncmQjamQdaWpbTvI79t4El/8nYm0ul/a1gOq5ckV8/A1DNT+NCab6LnXTF/Bm4rP2H" +
            "HTQNf1k4X0zQ+iOK2e/sj93/KGXwe+7xI/Q7Yq4z+B4DxH9Nq170yhx64DQUynb5VnpuIQbGDjrnI4q3" +
            "zvfLqdgtFwvUfN5+zWfSCUFzAKcR5b09h9XXNyAXe2jh0zjwyBO9lB/rNQh9D78B2+x1kuSc9FZMNpuq" +
            "/uTgPpUV2GfPb6NQZn9tzTVLZDpd8+G3K7LmR1/nU82Xz2fM+5MxUiPSuJi/w1dS3gesNKXBy1d8ApPN" +
            "vuZG7Hfocc4gv770beXYjCuuwxnn+S+LdB+1PwYcfXvqO3yMKcy3sf11cKux2il7zmmn5G3FYbJRO2zv" +
            "fU7WrUWY5iPA37rmR1/n68xXy8W1CCIaX6y/q1c2cBe0rH0TL732hubEj5avxA233uPhcy/5DV5/PvX7" +
            "QBfNuhp33e/fPt5m/BnotcuRSiddJ9jXa9v8qXjW8IxzzJ5f/PqMU1BIGyVngLp1yLfmW9LxKJpPx5E8" +
            "DSyO5psaWTZ4V6cJX22qxj0PPKw4saauTsHx9F27Pz72lD/4JWXoe8gc9LQH32mmznz7bmDNK3MdPbbt" +
            "zrlXOd8xKKTZK5pteqV+zcRqtP94t67x7dH8TOt8qWLQuYJ93bjOfBCmw8D8YOwzX58D+MHENGb2/Nb3" +
            "UPfOo/aaEBfM+C2Wvv0eth+Wer2qasUqxak/+8kPnTX+mefP9PZVTpuBbkO+E1i/va199SYk0t8ssl8k" +
            "Hbv3d1AMGzZ0CJZVrQBv2qoMVvh3CFMYhuNhTI/KfBenwp+TiQnRIJ35KexHmo8hTwjBjZrvM9MvH+81" +
            "EH2mXogt/7zKOffhxxcZnWn/FIx92/bYk/zvBlRMuQjdh48NZL4z+P++Dc0r/+GUt1P/3Dm5vwCarQ0U" +
            "zwWWYQWslq2Kv4L8rWs+kC/NV8fNexYAn+mhd6LUiYjpeb7CfJgiHD525x5p3HPUVPQ9aDZiPYOXffZb" +
            "PCf+4hzn/r5tvfe7GD3EpM/UWbeT9uA3LU89Z7Cf1f/5sfu9tFwMc19n4801vv+gthMg/gqc7VOMDLN9" +
            "SOOo1md/iBdf86Fgzwnp4CkfMRHl209Ay5fvecdZaXchDw+j5bN/K46tnHYZuo+cHMr8uqX3e4Pfe5te" +
            "ePbRed6XOotlA/qnfmACVkJkgXrEuvVyIM9B873xQCbm6+fBMPGMc03jo2u+aXZPMwHnwZqMEFy27e5+" +
            "ZwTuLWb3Na2NSNSsQfmYg8R/h6JUyEZY/fUiaJo+SL1b2K1bGZ5ecK/zHcJi24B+/pNG3rxFNKYXwjVf" +
            "n+3nS/MBwxxAZjrFmuaTzAByEUTUfKSxfBMJIDIDf3DtOUK/w66F1MBQ5td/+CQa3vF/GvbBu+YW9IZP" +
            "mFVIbxtZ4tFwLKPm87xrPsVurMTy9TyfHjdGONF8744VSBB5naFzCoQ6xS3X+PFf0bDkPme//YXRR+bd" +
            "Km73TkVHmf29BtfsR97ZaL5ClnZoPoz1Q58D5F/zaaYwB1e+fm+/deMy1L12C1K7GObfcT0OO3gaOtLk" +
            "J4082VrUdb6ClfNS9frfDeRqJlCdDwPzYcTcgIv1e/u8rRk1i3/nOXvGhWfh6Dy+WZSryd9mZsnUV8dV" +
            "ZkJjvsz0aPf2DcwHwUr9qePaHKBQ63y3vK75CGa+c9lozLetYdlTsBq+cj5PmvBdXHTOaegMJn+vwbJ/" +
            "34hTzQcKtc43Mt8rD/d7Aea0nO91vlnzIXUmWPODmC8HS2P6nUL7t/7+ePeNBX3Ik43JGQDpH48o1jqf" +
            "1q9i6a+GFWudn96BfP/efsunL4tbralHwT/54fSC/m5gthaX3zgWzyOKuc6nmm+YAwRrfqAGF1rzEU3z" +
            "ZdxQ9azn41/+9AR0WmOpf9qv+Tyz5gOa5mtzgM6yznebbmK6iflyeSuZQGJD6p1C+xfD5ZcwOoN53XWM" +
            "BTBf35qZb9Z8ir2ETTRfmwPozNaxKROYmF2sdb6zATycTL9qZduU9N8E6EzGpQhwekE1GWi35sOo+dAw" +
            "PAx/DuDtlCMqsuYXd53vd9W/XrLBf2t420HFvc+frRV7nU+xW52bsePqHICFMt+PKBVHubcflFl4hOtl" +
            "+r19++fZXJt7+72Yv2AhOpPV1dX7gCEjidpzb1+rD+E4LmsFKJMhMx8kE6Do63y/mX5KtYtbrf6LpPaL" +
            "F53aSrohSOYiM59HX+dTzCAv7ZF6J1BlfqZ3+IrLfEsKBtk5Mu42bCzaxCTQat6q+Rs0XUrI3xtiPLxk" +
            "xnr8lOV8OSU+aI+8MN+EU+8IBmQCWn/a2G5XbeDwRSHVEQkDMvODceiDo4Clil2OU2dBcqZhsE3YL841" +
            "56v1p/7hEbCfGTltHgBuxJ7/JKxcQcIsY30Gf4FLbfIHjGfEvubLDnHuA2iD5WEX6vi/6e/qKeWZv0fV" +
            "YHq886zz6QSTQ/Wr99fDgc67zqear9ZPOsf0zirtN+BUcUaYmSqhzZHk8pxLzSCrHhBSGLbghV/nU82X" +
            "sX08pjCuk67zpY0SDEGYacxnhNnMwHwyGCCDRZhjYqKJ+TpTkYH5yOs6P5O/4jrzw5conhOKuM7noMw3" +
            "dwbKYKSbIWFuwP7gqMxHCJYzCcWdbZ3v+gEGDHUOIDk135rPstF8MvgE0wwkY6Pmc4KBUKaEvyGlYhhw" +
            "sZ/nZ6P5srn+ionBrFEiy8sEiKj5QBTNpziI+SBM15hP2oNMmk/7A/hM5uGaH6StnpYG4KCt4me/Yp35" +
            "PH/rfNpOKH7gNTGLW+vdi3f083xVk6GeH4I7k+YjJBNko/mhcwrmtx8EG/3FqL9SJrbr7b8Yst6rlGSC" +
            "nDU/jaMwnwFksHSsdEbd4V/PbQaA7DTfa6GSCWScjearmUUuB5hkUj5PDUI5I/nXDdZ8lfnUL3o/HWQH" +
            "ANZ3rfNlpshBr2IEMLUzr/NV/6j+Ev9bL1YBfL3CnIya37XONzNf35qZD535BswM7TDhTOt8QA0Kd086" +
            "9taLOQD8OQCiaH4Y8xHqFBMTVadDCYYg3LXOB+lvJn/px9OxtDomWvO6dxF5sLrW+X6QI5j5nX2dL10e" +
            "asZz8DuxqpmDF4vB/VJhftc6nzATaL/mo0PW+RRzH6/feu/4xXYG4GIpuMirXNN8BDPf/qdrnZ8983nR" +
            "1vlw91D5EyR/RpzNnZ+IifHY0146I4zzz+HQmJ+hs1qal8sBpJHBuGudb+qP5A9G/UWOAwZ/Wc6PLKX/" +
            "1Obmf4lW1kbW/DSOwnymXVzHtLHSDv96bjMAZKf5XguVTCDjbDRfzSxyOcAkk/J5ahDKGcm/brDmq8yn" +
            "ftH7Cal+6hdeV1vb+1/2PicAls3erZVz6w6vcV3rfLRf8zt+nU80H1KmvB0Ld3O+ouT9sd227q1zRKGa" +
            "rnU+Apivb83Mh858A2aGdphwe9b5AXO5jSUt7Or0AT8APrlkVI0oc0048xHqFBMTVadDCYYg3LXOh5bJ" +
            "wv2lH9f85TYDfM7mh8bXuuWVP7fdzBI3ikXBBo35bq94eCYwXzyNWebOQBkMpCMe6Frn+/2UMYgf1Iyn" +
            "k0JsVtdaLXdCMiUAVs8e2SyKztaY37XO95ieFfNBsFK/394CrfNBMyVn+A3mT2uW61ECwLblGHa3KPtn" +
            "zzVd6/zsmc87wzqf+pk9Vzd8/IMgpgUAZjMr0dL6Y3FaFe087ayW5uVyblOMkavirnW+qT+SPxj1FzkO" +
            "6P7iir+Wl8RLT7DHFsQYAmzM7E934smY/QfvekhX8y5GMQtsjN5YuT5lD0/9wyNgn+mcNg9QMoVUhntH" +
            "yG4ds4z16WneOZOrOPX/TFjSfC67Q8V+/VI7lCIGDFbDkRxXN2/SchgshgD7ePZIkQFivwxiPoIyAQdJ" +
            "f5DKRdB8A6aZhQWVZ/6ecM3/xq/z0/U7jD8haPBtCwwA21ZcOWyBqOTacM3vWufDgKPMRQI1X+m3GhTu" +
            "nsyab3+yrqydN+E5hBhDBBs949OjxBUWiMI9gtK8q4VRNV+NdH9wLW5aeukYIE431JdydmF/bz8Mp+pX" +
            "sV+/5D+/+UYszcMV7PlLvu2ewo2iXyfW3b/vM8hgkQLAttGzVu8pHhs+KU4YoQ0+iqn53oGM2NdDTnbr" +
            "OPv6DZovM7CjNF+s9WMx/GDrfRPVP6sSYDFEtJVXjni71CrdR1ziNY35gJJuAUTXfE4wJA1jenB1rfN1" +
            "7PlLjA1PluwTdfBtixwAtlXNGVo9rHT1FLoH21MAAAEpSURBVJEJThWXXWfv61rn6zhrzafk0YIiXPMt" +
            "8HUCnlY7vGVK3YPjqpGFRZYAakPP+6K8W/fE2eK58iUC9nH3d3bNz/bv6uVP82n9kr9y1XyGLVbSurau" +
            "gd2MhRObkIPlHACuDb/ksz4lrO0yUdXPRKv6Kgfzovlcrk4uWGDN79Tr/M2iwjtYWeL6mjsmb0E7rN0B" +
            "4NlxvGTEqJWTRZQfIyqdLiJ1uyDmUxzGfAXLt6UlJujYxHwzE/PF/Kj1y1hnelB/nB1rxXlPc558sqZ+" +
            "7WIsPD6JPFj+AoDYyEuXTxZ3EvcXFxgqujAQjA8SfRkE+zPnzt1FncC5zMYRyvQwHK1+JmUyH6vVBOMo" +
            "zJdwo9huFHiDGGyxteztF0nO/lE3b5z651LyZP8PAAD//xrXMsAAAAAGSURBVAMAL0Vf6dyXgGoAAAAA" +
            "SUVORK5CYII=";

    private static Bitmap cachedBitmap = null;

    public static synchronized Bitmap getLogoBitmap() {
        if (cachedBitmap == null || cachedBitmap.isRecycled()) {
            try {
                byte[] decoded = Base64.decode(NNVC_LOGO_BASE64, Base64.DEFAULT);
                cachedBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            } catch (Throwable ignored) {}
        }
        return cachedBitmap;
    }

    public static Drawable getLogoDrawable(Context context) {
        Bitmap bmp = getLogoBitmap();
        if (bmp != null) {
            return new BitmapDrawable(context.getResources(), bmp);
        }
        return null;
    }

    public static ImageView createLogoView(Context context, int sizePx) {
        ImageView iv = new ImageView(context);
        Bitmap bmp = getLogoBitmap();
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(sizePx, sizePx);
        iv.setLayoutParams(lp);
        return iv;
    }
}
