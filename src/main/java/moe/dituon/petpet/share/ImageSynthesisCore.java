package moe.dituon.petpet.share;

import com.yy.mobile.emoji.EmojiReader;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ImageSynthesisCore {

    /**
     * 在Graphics2D画布上 绘制缩放头像
     *
     * @param g2d         Graphics2D 画布
     * @param avatarImage 处理后的头像
     * @param pos         处理后的坐标 (int[4]{x, y, w, h})
     * @param angle       旋转角, 对特殊角度有特殊处理分支
     * @param isRound     裁切为圆形
     */
    protected static void g2dDrawZoomAvatar(Graphics2D g2d, BufferedImage avatarImage, int[] pos,
                                            float angle, boolean isRound) {
        g2dDrawZoomAvatar(g2d, avatarImage, pos, angle, isRound, 1.0F, FitType.FILL, 1.0F);
    }

    /**
     * 在Graphics2D画布上 绘制缩放头像
     *
     * @param g2d         Graphics2D 画布
     * @param avatarImage 处理后的头像
     * @param pos         处理后的坐标 (int[4]{x, y, w, h})
     * @param angle       旋转角, 对特殊角度有特殊处理分支
     * @param isRound     裁切为圆形
     * @param multiple    缩放倍数
     * @param fitType     显示策略
     * @param opacity     头像不透明度
     */
    protected static void g2dDrawZoomAvatar(
            Graphics2D g2d, @NotNull BufferedImage avatarImage, int[] pos,
            float angle, boolean isRound, float multiple, FitType fitType, float opacity
    ) {
        int x = (int) (pos[0] * multiple);
        int y = (int) (pos[1] * multiple);
        int w = (int) (pos[2] * multiple);
        int h = (int) (pos[3] * multiple);
        BufferedImage newAvatarImage = avatarImage;
//        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
//        g2d.setComposite(AlphaComposite.Src);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (fitType) {
            case COVER: {
                double ratio = Math.min(
                        (double) avatarImage.getWidth() / w,
                        (double) avatarImage.getHeight() / h
                );
                int resultWidth = (int) (w * ratio);
                int resultHeight = (int) (h * ratio);
                if (resultWidth <= 0 || resultHeight <= 0) return;

                int[] cropPos = new int[]{
                        0, 0, resultWidth, resultHeight
                };

                newAvatarImage = cropImage(newAvatarImage, cropPos);
                break;
            }
            case CONTAIN: {
                int resultWidth = w;
                int resultHeight = h;
                double avatarRatio = (double) avatarImage.getWidth() / avatarImage.getHeight();
                double canvasRatio = (double) w / h;
                if (avatarRatio > canvasRatio) {
                    resultHeight = (int) (w / avatarRatio);
                } else {
                    resultWidth = (int) (h * avatarRatio);
                }

                int resultX = (w - resultWidth) / 2;
                int resultY = (h - resultHeight) / 2;
                x = x + resultX;
                y = y + resultY;
                w = resultWidth;
                h = resultHeight;

                break;
            }
        }

        if (angle == 0) {
            g2d.drawImage(newAvatarImage, x, y, w, h, null);
            return;
        }

        if (isRound || angle % 90 == 0) {
            BufferedImage roundedImage = new BufferedImage(newAvatarImage.getWidth(), newAvatarImage.getHeight(), newAvatarImage.getType());
            Graphics2D rotateG2d = roundedImage.createGraphics();
            rotateG2d.rotate(Math.toRadians(angle), newAvatarImage.getWidth() / 2.0, newAvatarImage.getHeight() / 2.0);
            rotateG2d.drawImage(newAvatarImage, null, 0, 0);
            rotateG2d.dispose();
            g2d.drawImage(roundedImage, x, y, w, h, null);
            return;
        }

        g2d.drawImage(rotateImage(newAvatarImage, angle), x, y, w, h, null);
    }

    /**
     * 在Graphics2D画布上 绘制变形头像
     *
     * @param g2d         Graphics2D 画布
     * @param avatarImage 处理后的头像
     * @param deformPos   头像四角坐标 (Point2D[4]{左上角, 左下角, 右下角, 右上角})
     * @param anchorPos   锚点坐标
     */
    protected static void g2dDrawDeformAvatar(Graphics2D g2d, BufferedImage avatarImage,
                                              Point2D[] deformPos, int[] anchorPos) {
        BufferedImage result = ImageDeformer.computeImage(avatarImage, deformPos);
        g2d.drawImage(result, anchorPos[0], anchorPos[1], null);
    }

    /**
     * 在Graphics2D画布上 绘制变形头像
     *
     * @param g2d         Graphics2D 画布
     * @param avatarImage 处理后的头像
     * @param deformPos   头像四角坐标 (Point2D[4]{左上角, 左下角, 右下角, 右上角})
     * @param anchorPos   锚点坐标
     * @param multiple    缩放倍数
     */
    protected static void g2dDrawDeformAvatar(Graphics2D g2d, BufferedImage avatarImage,
                                              Point2D[] deformPos, int[] anchorPos, float multiple) {
        for (Point2D point : deformPos) {
            point.setLocation(point.getX() * multiple, point.getY() * multiple);
        }
        BufferedImage result = ImageDeformer.computeImage(avatarImage, deformPos);
        g2d.drawImage(result,
                (int) (anchorPos[0] * multiple), (int) (anchorPos[1] * multiple), null);
    }

    /**
     * 在Graphics2D画布上 绘制文字
     *
     * @param g2d   Graphics2D 画布
     * @param text  文本数据
     * @param pos   坐标 (int[2]{x, y})
     * @param color 颜色
     * @param font  字体
     */
    protected static void g2dDrawText(Graphics2D g2d, String text, int[] pos, Color color, Font font) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(color);
        g2d.setFont(font);
        if (text.contains("\n")) {
            String[] texts = text.split("\n");
            int y = pos[1];
            short height = (short) TextModel.getTextHeight(text, font);
            for (String txt : texts) {
                drawString(g2d, txt, pos[0], y, font);
                y += height + TextModel.LINE_SPACING;
            }
            return;
        }
        drawString(g2d, text, pos[0], pos[1], font);
    }

    protected static void drawString(Graphics2D g2d, String str, int x, int y, Font font){
        List<EmojiReader.Node> nodes = EmojiReader.INSTANCE.analyzeText(str);
        int xIng = x;
        for (EmojiReader.Node node : nodes) {
            char[] chars = Character.toChars(node.getCodePoint().get(0));
            String s = String.valueOf(chars);
            int textWidth = TextModel.getTextWidth(s, font);
            if (node.isEmoji()){
                // emoji
                List<Integer> codePoint = node.getCodePoint();
                StringBuilder sb = new StringBuilder();
                for (Integer cp : codePoint) {
                    sb.append(String.format("-%x", cp));
                }
                String emojiCode = sb.substring(1);
                try{
                    short height = (short) TextModel.getTextHeight(s, font);
                    File emoji = new File("./data/emoji/png/" + emojiCode + ".png");
                    // emoji不存在
                    if (!emoji.exists())continue;

                    BufferedImage emojiImage = ImageIO.read(emoji);
                    g2d.drawImage(emojiImage, xIng, y - height, height, height, null);
                    xIng += height + 2;
                }catch (Exception e){
                    e.printStackTrace();
                    g2d.drawString("?", xIng, y);
                    xIng += TextModel.getTextWidth("?", font);;
                }
            }else{
                g2d.drawString(s, xIng, y);
                xIng += textWidth;
            }
        }
    }

    /**
     * 在Graphics2D画布上 绘制带有描边的文字
     *
     * @param g2d         Graphics2D 画布
     * @param text        文本数据
     * @param pos         坐标 (int[2]{x, y})
     * @param color       颜色
     * @param font        字体
     * @param strokeSize  描边宽度
     * @param strokeColor 描边颜色
     */
    protected static void g2dDrawStrokeText(Graphics2D g2d, String text, int[] pos,
                                            Color color, Font font,
                                            short strokeSize, Color strokeColor) {
        BasicStroke outlineStroke = new BasicStroke(strokeSize);
        Color originalColor = g2d.getColor();
        Stroke originalStroke = g2d.getStroke();
        RenderingHints originalHints = g2d.getRenderingHints();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setStroke(outlineStroke);

        if (!text.contains("\n")) {
            int y = pos[1];
            g2dDrawStrokeTextLine(g2d, text, pos, color, font, strokeColor, y);
        } else {
            String[] texts = text.split("\n");
            int y = pos[1];

            short height = (short) TextModel.getTextHeight(text, font);
            for (String txt : texts) {
                g2dDrawStrokeTextLine(g2d, txt, pos, color, font, strokeColor, y);
                y += height + strokeSize * 2 + 2;
            }
        }

        g2d.setColor(originalColor);
        g2d.setStroke(originalStroke);
        g2d.setRenderingHints(originalHints);
    }

    private static void g2dDrawStrokeTextLine(Graphics2D g2d, String text, int[] pos, Color color, Font font, Color strokeColor, int y) {


        List<EmojiReader.Node> nodes = EmojiReader.INSTANCE.analyzeText(text);
        int xIng = pos[0];
        for (EmojiReader.Node node : nodes) {
            g2d.setColor(strokeColor);
            char[] chars = Character.toChars(node.getCodePoint().get(0));
            String s = String.valueOf(chars);
            int textWidth = TextModel.getTextWidth(s, font);
            if (node.isEmoji()) {

                // emoji
                List<Integer> codePoint = node.getCodePoint();
                StringBuilder sb = new StringBuilder();
                for (Integer cp : codePoint) {
                    sb.append(String.format("-%x", cp));
                }
                String emojiCode = sb.substring(1);

                try{
                    short height = (short) TextModel.getTextHeight(s, font);
                    File emoji = new File("./data/emoji/png/" + emojiCode + ".png");
                    // emoji不存在
                    if (!emoji.exists())continue;

                    BufferedImage emojiImage = ImageIO.read(emoji);
                    g2d.drawImage(emojiImage, xIng, y - height, height, height, null);
                    xIng += height + 2;
                }catch (Exception e){
                    e.printStackTrace();
                    g2d.drawString("?", xIng, y);
                    xIng += TextModel.getTextWidth("?", font);;
                }
            }else {

                GlyphVector glyphVector = font.createGlyphVector(g2d.getFontRenderContext(), s);
                Shape textShape = glyphVector.getOutline();
                AffineTransform transform = new AffineTransform();
                transform.translate(xIng, y);
                textShape = transform.createTransformedShape(textShape);

                g2d.draw(textShape);
                g2d.setColor(color);
                g2d.fill(textShape);
                xIng += textWidth;
            }
        }
    }

    /**
     * 将图像裁切为圆形
     *
     * @param input     输入图像
     * @param antialias 抗锯齿
     * @return 裁切后的图像
     */
    public static BufferedImage convertCircular(BufferedImage input, boolean antialias) {
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        Ellipse2D.Double shape = new Ellipse2D.Double(0, 0, input.getWidth(), input.getHeight());
        Graphics2D g2 = output.createGraphics();
        g2.setClip(shape);

        if (antialias) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        g2.drawImage(input, 0, 0, null);
        g2.dispose();
        return output;
    }

    /**
     * 将图像裁切为圆形
     *
     * @param inputList 输入图像数组
     * @param antialias 抗锯齿
     * @return 裁切后的图像
     */
    public static List<BufferedImage> convertCircular(List<BufferedImage> inputList, boolean antialias) {
        return inputList.stream()
                .map(input -> convertCircular(input, antialias))
                .collect(Collectors.toList());
    }

    /**
     * 完整旋转图像 (旋转时缩放以保持图像完整性)
     *
     * @param avatarImage 输入图像
     * @param angle       旋转角度
     * @return 旋转后的图像
     */
    public static BufferedImage rotateImage(BufferedImage avatarImage, float angle) {
        double sin = Math.abs(Math.sin(Math.toRadians(angle))),
                cos = Math.abs(Math.cos(Math.toRadians(angle)));
        int w = avatarImage.getWidth();
        int h = avatarImage.getHeight();
        int neww = (int) Math.floor(w * cos + h * sin),
                newh = (int) Math.floor(h * cos + w * sin);
        BufferedImage rotated = new BufferedImage(neww, newh, avatarImage.getType());
        Graphics2D g2d = rotated.createGraphics();
        rotated = g2d.getDeviceConfiguration().createCompatibleImage(
                rotated.getWidth(), rotated.getHeight(), Transparency.TRANSLUCENT);
        g2d.dispose();
        g2d = rotated.createGraphics();

        g2d.translate((neww - w) / 2, (newh - h) / 2);
        g2d.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
        g2d.drawRenderedImage(avatarImage, null);
        g2d.dispose();
        return rotated;
    }

    /**
     * 从URL获取网络图像
     *
     * @param imageUrl 图像URL
     */
    public static BufferedImage getWebImage(String imageUrl) {
        try {
            return ImageIO.read(new URL(imageUrl));
        } catch (Exception ex) {
            throw new RuntimeException("[获取图像失败]  URL: " + imageUrl, ex);
        }
    }

    /**
     * 从URL获取网络图像 (支持GIF)
     *
     * @param imageUrl 图像URL
     * @return GIF全部帧 或一张静态图像
     */
    public static List<BufferedImage> getWebImageAsList(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            return getImageAsList(new BufferedInputStream(url.openStream()));
        } catch (Exception ex) {
            throw new RuntimeException("[获取/解析 图像失败]  URL: " + imageUrl, ex);
        }
    }

    /**
     * 从BufferedInputStream获取图像 (支持GIF)
     *
     * @param inputStream 图像输入流, 必须支持mark()
     * @return GIF全部帧 或一张静态图像
     */
    public static List<BufferedImage> getImageAsList(InputStream inputStream) throws IOException {
        ReusableGifDecoder decoder = new ReusableGifDecoder();
        inputStream.mark(0); //循环利用inputStream, 避免重复获取
        decoder.read(inputStream);

        if (decoder.err()) {
            inputStream.reset();
            List<BufferedImage> list = List.of(ImageIO.read(ImageIO.createImageInputStream(inputStream)));
            inputStream.close();
            return list;
        }
        inputStream.close();
        List<BufferedImage> output = new ArrayList<>(decoder.getFrameCount());
        for (short i = 0; i < decoder.getFrameCount(); i++) {
            output.add(decoder.getFrame(i));
        }
        return output;
    }

    /**
     * 裁切图像
     *
     * @param image   输入图像
     * @param cropPos 裁切坐标 (int[4]{x1, y1, x2, y2})
     * @return 裁切后的图像
     */
    public static BufferedImage cropImage(BufferedImage image, int[] cropPos) {
        return cropImage(image, cropPos, false);
    }

    /**
     * 裁切图像
     *
     * @param image     输入图像
     * @param cropPos   裁切坐标 (int[4]{x1, y1, x2, y2})
     * @param isPercent 按百分比处理坐标
     * @return 裁切后的图像
     */
    public static BufferedImage cropImage(BufferedImage image, int[] cropPos, boolean isPercent) {
        int width = cropPos[2] - cropPos[0];
        int height = cropPos[3] - cropPos[1];
        if (isPercent) {
            width = (int) ((float) width / 100 * image.getWidth());
            height = (int) ((float) height / 100 * image.getHeight());
        }
        BufferedImage croppedImage = new BufferedImage(width, height, image.getType());
        Graphics2D g2d = croppedImage.createGraphics();
        if (isPercent) { //百分比
            g2d.drawImage(image, 0, 0, width, height,
                    (int) ((float) cropPos[0] / 100 * image.getWidth()),
                    (int) ((float) cropPos[1] / 100 * image.getHeight()),
                    (int) ((float) cropPos[2] / 100 * image.getWidth()),
                    (int) ((float) cropPos[3] / 100 * image.getHeight()), null);
        } else { //像素
            g2d.drawImage(image, 0, 0, width, height
                    , cropPos[0], cropPos[1], cropPos[2], cropPos[3], null);
        }
        g2d.dispose();
        return croppedImage;
    }

    /**
     * 裁切图像
     *
     * @param imageList 输入图像数组
     * @param cropPos   裁切坐标 (int[4]{x1, y1, x2, y2})
     * @param isPercent 按百分比处理坐标
     * @return 裁切后的图像
     */
    public static List<BufferedImage> cropImage(List<BufferedImage> imageList,
                                                int[] cropPos, boolean isPercent) {
        return imageList.stream()
                .map(image -> cropImage(image, cropPos, isPercent))
                .collect(Collectors.toList());
    }

    /**
     * 镜像翻转图像
     */
    public static BufferedImage mirrorImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage mirroredImage;
        Graphics2D g2d;
        (g2d = (mirroredImage = new BufferedImage(width, height, image
                .getColorModel().getTransparency())).createGraphics())
                .drawImage(image, 0, 0, width, height, width, 0, 0, height, null);
        g2d.dispose();
        return mirroredImage;
    }

    /**
     * 镜像翻转图像数组
     */
    public static List<BufferedImage> mirrorImage(List<BufferedImage> imageList) {
        return imageList.stream().map(ImageSynthesisCore::mirrorImage).collect(Collectors.toList());
    }

    /**
     * 竖直翻转图像
     */
    public static BufferedImage flipImage(BufferedImage image) {
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(),
                image.getType());
        AffineTransform tran = AffineTransform.getTranslateInstance(0,
                image.getHeight());
        AffineTransform flip = AffineTransform.getScaleInstance(1d, -1d);
        tran.concatenate(flip);
        Graphics2D g2d = flipped.createGraphics();
        g2d.setTransform(tran);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return flipped;
    }

    /**
     * 竖直翻转图像数组
     */
    public static List<BufferedImage> flipImage(List<BufferedImage> imageList) {
        return imageList.stream()
                .map(ImageSynthesisCore::flipImage)
                .collect(Collectors.toList());
    }

    /**
     * 图像灰度化
     */
    public static BufferedImage grayImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y));
                int gray = (int) (color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114);
                Color color_end = new Color(gray, gray, gray);
                grayscaleImage.setRGB(x, y, color_end.getRGB());
            }
        }
        return grayscaleImage;
    }

    /**
     * 灰度化图像数组
     */
    public static List<BufferedImage> grayImage(List<BufferedImage> imageList) {
        return imageList.stream().map(ImageSynthesisCore::grayImage).collect(Collectors.toList());
    }

    /**
     * 图像二值化
     */
    public static BufferedImage binarizeImage(BufferedImage image) {
        int h = image.getHeight();
        int w = image.getWidth();
        BufferedImage binarizeImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int val = image.getRGB(i, j);
                int r = (0x00ff0000 & val) >> 16;
                int g = (0x0000ff00 & val) >> 8;
                int b = (0x000000ff & val);
                int m = (r + g + b);
                if (m >= 383) {
                    binarizeImage.setRGB(i, j, Color.WHITE.getRGB());
                } else {
                    binarizeImage.setRGB(i, j, 0);
                }
            }
        }
        return binarizeImage;
    }

    /**
     * 二值化图像数组
     */
    public static List<BufferedImage> binarizeImage(List<BufferedImage> imageList) {
        return imageList.stream().map(ImageSynthesisCore::binarizeImage).collect(Collectors.toList());
    }

    /**
     * BufferedImage转为int[][]数组
     */
    public static int[][] convertImageToArray(BufferedImage bf) {
        int width = bf.getWidth();
        int height = bf.getHeight();
        int[] data = new int[width * height];
        bf.getRGB(0, 0, width, height, data, 0, width);
        int[][] rgbArray = new int[height][width];
        for (int i = 0; i < height; i++)
            System.arraycopy(data, i * width, rgbArray[i], 0, width);
        return rgbArray;
    }
}
