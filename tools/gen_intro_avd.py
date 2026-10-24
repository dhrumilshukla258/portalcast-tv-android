# Generates portalcast-tv-android res/drawable/intro_logo.xml: the cold-start intro as an
# AnimatedVectorDrawable, which Android animates on the RenderThread (smooth while the main
# thread builds the app). Numbers are the intro's own (ui/intro/IntroScreen.kt, webui
# index.html); this script is the source of the XML. Usage: gen_intro_avd.py OUT.xml [end]
# ("end" writes a static vector at the last frame's values, to compare with the logo).
import sys

W, H = 1920, 1080
k = W * 0.42 / 398            # logo 42% of the width
x0, y0 = W / 2 - 199 * k, H / 2 - 48 * k
SLIDE = 167 / 1.1             # P starts in the middle (viewport 199 vs its home 32)
FROM0, FROM_SLOPE = (25.9 + 167) / 1.1 + 2, -(167 / 1.1) - 20   # reveal window's left edge
f = lambda v: f"{v:.3f}".rstrip("0").rstrip(".")

def window(u):
    a = FROM0 + FROM_SLOPE * u
    b = a + 345 * u
    return f"M{f(a)},-20 H{f(b)} V100 H{f(a)} Z"

end = len(sys.argv) > 2 and sys.argv[2] == "end"
G = '''<aapt:attr name="android:strokeColor"><gradient android:type="linear" android:startX="16" android:startY="10" android:endX="88" android:endY="92"><item android:offset="0" android:color="#FF22D3EE"/><item android:offset="0.5" android:color="#FF3B82F6"/><item android:offset="1" android:color="#FF7C5CFF"/></gradient></aapt:attr>'''
LETTERS = ("M45,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M90,56 V24 M90,38 A14,14 0 0,1 104,24 M121,8 V46 A10,10 0 0,0 131,56 H133 M113,24 H131 "
           "M146,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M178,24 V56 M191,4 V56 M232.26,29.72 A16,16 0 1,0 232.26,50.28 "
           "M245,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M277,24 V56 M308.66,28 A10,8 0 1,0 300,40 A10,8 0 1,1 291.34,52 "
           "M323,8 V46 A10,10 0 0,0 333,56 H335 M315,24 H333")
a0 = "1" if end else "0"      # the P's alphas before it fades in
z0 = f(0.84 if end else 0.84 * 0.85)

vector = f'''<vector android:width="960dp" android:height="540dp" android:viewportWidth="{W}" android:viewportHeight="{H}">
        <path android:name="bg" android:pathData="M0,0 H{W} V{H} H0 Z" android:fillColor="#FF05070F"/>
        <group android:translateX="{f(x0)}" android:translateY="{f(y0)}" android:scaleX="{f(k)}" android:scaleY="{f(k)}">
            <group android:translateX="32" android:translateY="6" android:scaleX="1.1" android:scaleY="1.1">
                <group android:translateX="-12">
                    <clip-path android:name="window" android:pathData="{window(1 if end else 0)}"/>
                    <path android:name="word" android:pathData="{LETTERS}" android:strokeColor="#FFEAF2FF" android:strokeWidth="7" android:strokeLineCap="round" android:strokeLineJoin="round"/>
                </group>
                <group android:name="slide" android:translateX="{f(0 if end else SLIDE)}">
                    <group android:name="sym" android:pivotX="46" android:pivotY="52" android:translateX="-46" android:translateY="-12" android:scaleX="{z0}" android:scaleY="{z0}">
                        <path android:name="disc" android:pathData="M29,52 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0" android:fillColor="#FF0B1226" android:fillAlpha="{a0}"/>
                        <path android:name="stem" android:pathData="M24,52 V88" android:strokeWidth="12" android:strokeLineCap="round" android:strokeAlpha="{a0}">{G}</path>
                        <path android:name="ring" android:pathData="M66.67,44.48 A22,22 0 1,1 53.52,31.33" android:strokeWidth="12" android:strokeLineCap="round" android:strokeAlpha="{a0}">{G}</path>
                        <path android:name="arc1" android:pathData="M62.43,21.1 A35,35 0 0,1 76.9,35.57" android:strokeWidth="6.5" android:strokeLineCap="round" android:trimPathEnd="{"1" if end else "0"}">{G}</path>
                        <path android:name="arc2" android:pathData="M71.05,13.42 A46,46 0 0,1 84.58,26.95" android:strokeWidth="6.5" android:strokeLineCap="round" android:strokeAlpha="0.55" android:trimPathEnd="{"1" if end else "0"}">{G}</path>
                        <path android:name="play" android:pathData="M41.5,44 L55.5,52 L41.5,60 Z" android:fillColor="#FFFFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="4" android:strokeLineJoin="round" android:fillAlpha="{a0}" android:strokeAlpha="{a0}"/>
                    </group>
                </group>
            </group>
        </group>
    </vector>'''

NS = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"'
if end:
    out = f'<?xml version="1.0" encoding="utf-8"?>\n' + vector.replace("<vector ", f"<vector {NS} ", 1) + "\n"
else:
    LOSI, FOSI, LIN = "@android:interpolator/linear_out_slow_in", "@android:interpolator/fast_out_slow_in", "@android:interpolator/linear"
    def anim(prop, frm, to, start, dur, interp, kind="floatType"):
        return f'<objectAnimator android:propertyName="{prop}" android:valueFrom="{frm}" android:valueTo="{to}" android:valueType="{kind}" android:startOffset="{start}" android:duration="{dur}" android:interpolator="{interp}"/>'
    fade_in = lambda prop: anim(prop, 0, 1, 0, 420, LOSI)
    fade_out = lambda prop, frm=1: anim(prop, frm, 0, 1580, 260, LIN)
    targets = {
        "bg": [fade_out("fillAlpha")],
        "word": [fade_out("strokeAlpha")],
        "window": [anim("pathData", window(0), window(1), 740, 520, FOSI, "pathType")],
        "slide": [anim("translateX", f(SLIDE), 0, 740, 520, FOSI)],
        "sym": [anim("scaleX", z0, f(0.84), 0, 420, LOSI), anim("scaleY", z0, f(0.84), 0, 420, LOSI)],
        "disc": [fade_in("fillAlpha"), fade_out("fillAlpha")],
        "stem": [fade_in("strokeAlpha"), fade_out("strokeAlpha")],
        "ring": [fade_in("strokeAlpha"), fade_out("strokeAlpha")],
        "arc1": [anim("trimPathEnd", 0, 1, 300, 280, LOSI), fade_out("strokeAlpha")],
        "arc2": [anim("trimPathEnd", 0, 1, 440, 300, LOSI), fade_out("strokeAlpha", 0.55)],
        "play": [fade_in("fillAlpha"), fade_in("strokeAlpha"), fade_out("fillAlpha"), fade_out("strokeAlpha")],
    }
    t = "\n".join(
        f'    <target android:name="{name}"><aapt:attr name="android:animation"><set android:ordering="together">{"".join(a)}</set></aapt:attr></target>'
        for name, a in targets.items())
    out = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by gen_intro_avd.py (do not edit by hand): the cold-start intro, animated by
     Android on the RenderThread so it stays smooth while the app starts. The P appears and
     zooms in, its arcs draw, it slides left while "ortalcast" comes out from behind it into the
     logo, then everything fades (1.84 s). Same numbers as the web intro (webui index.html). -->
<animated-vector {NS}>
    <aapt:attr name="android:drawable">
    {vector}
    </aapt:attr>
{t}
</animated-vector>
'''
open(sys.argv[1], "w", encoding="utf-8", newline="\n").write(out)
print("wrote", sys.argv[1])
