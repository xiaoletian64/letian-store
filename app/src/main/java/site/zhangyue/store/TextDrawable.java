package site.zhangyue.store;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** TextDrawable — 无网络时的占位图标（应用名首字 + 稳定配色）。 */
public final class TextDrawable extends android.graphics.drawable.Drawable {
    private static final int[] PALETTE = {
            Ui.BLUE, Ui.RED, Ui.GREEN, 0xFF9A5CF5,
            0xFFF08C1A, 0xFF14919B, 0xFFD6409F, 0xFF5F6B7A
    };

    private final String letter;
    private final int bg;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TextDrawable(String name) {
        int h = Math.abs((name == null ? "?" : name).hashCode());
        bg = PALETTE[h % PALETTE.length];
        letter = firstLetter(name);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public static TextDrawable forApp(String name) {
        return new TextDrawable(name);
    }

    private static String firstLetter(String name) {
        if (name == null || name.isEmpty()) return "?";
        return new String(Character.toChars(name.codePointAt(0))).toUpperCase();
    }

    @Override
    public void draw(Canvas canvas) {
        RectF r = new RectF(getBounds());
        paint.setColor(bg);
        canvas.drawRoundRect(r, r.width() * 0.28f, r.width() * 0.28f, paint);
        textPaint.setTextSize(r.width() * 0.46f);
        float y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
        canvas.drawText(letter, r.centerX(), y, textPaint);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
