package moe.dituon.petpet.share;

import kotlin.NotImplementedError;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AvatarModel {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(BasePetService.DEFAULT_THREAD_POOL_SIZE);

    private Type imageType;
    protected AvatarType type;
    protected int[][] pos = {{0, 0, 100, 100}};
    protected List<Position> position;
    protected FitType fitType;
    protected short angle;
    protected float opacity = 1.0F;
    protected boolean round;
    protected boolean rotate;
    protected boolean onTop;
    protected List<BufferedImage> imageList = null;
    private short posIndex = 0;
    private boolean antialias;
    private boolean resampling;
    private AvatarPosType posType;
    private DeformData deformData = null;
    private CropType cropType;
    private int[] cropPos;
    private List<AvatarStyle> styleList;
    private short frameIndex = 0;

    @Deprecated
    public AvatarModel(AvatarData data, AvatarExtraDataProvider extraData, Type imageType) {
        setImage(data.getType(), extraData);
        buildData(data, imageType);
    }

    public AvatarModel(AvatarData data, GifAvatarExtraDataProvider extraData, Type imageType) {
        setImage(data, data.getType(), extraData);
        buildData(data, imageType);
    }

    private void buildData(AvatarData data, Type imageType) {
        type = data.getType();
        posType = data.getPosType() != null ? data.getPosType() : AvatarPosType.ZOOM;
        setPos(data.getPos(), this.imageType = imageType);
        position = data.getPosition();
        cropType = data.getCropType();
        setCrop(data.getCrop());
        fitType = data.getFit();
        styleList = data.getStyle();
        angle = data.getAngle() != null ? data.getAngle() : 0;
        opacity = data.getOpacity() != null ? data.getOpacity() : 1.0F;
        round = Boolean.TRUE.equals(data.getRound());
        rotate = Boolean.TRUE.equals(data.getRotate());
        onTop = Boolean.TRUE.equals(data.getAvatarOnTop());
        antialias = Boolean.TRUE.equals(data.getAntialias());
        resampling = Boolean.TRUE.equals(data.getResampling());
        buildImage();
    }

    private void setImage(AvatarType type, AvatarExtraDataProvider extraData) {
        imageList = new ArrayList<>(1);
        switch (type) {
            case FROM:
                imageList.add(Objects.requireNonNull(extraData.getFromAvatar()).invoke());
                break;
            case TO:
                imageList.add(Objects.requireNonNull(extraData.getToAvatar()).invoke());
                break;
            case GROUP:
                imageList.add(Objects.requireNonNull(extraData.getGroupAvatar()).invoke());
                break;
            case BOT:
                imageList.add(Objects.requireNonNull(extraData.getBotAvatar()).invoke());
                break;
            case RANDOM:
                imageList.add(Objects.requireNonNull(extraData.getRandomAvatar()).invoke());
                break;
        }
    }

    private void setImage(AvatarData data, AvatarType type, GifAvatarExtraDataProvider extraData) {
        switch (type) {
            case FROM:
                imageList = Objects.requireNonNull(extraData.getFromAvatar()).invoke();
                break;
            case TO:
                imageList = Objects.requireNonNull(extraData.getToAvatar()).invoke();
                break;
            case GROUP:
                imageList = Objects.requireNonNull(extraData.getGroupAvatar()).invoke();
                break;
            case BOT:
                imageList = Objects.requireNonNull(extraData.getBotAvatar()).invoke();
                break;
            case LOCAL:
//                throw new NotImplementedError("使用本地图片作为绘制材料");
                if (data.getLocalName() != null)
                    imageList = new LinkedList<>() {{
                        try {
                            add(ImageIO.read(new File(data.getLocalName())));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }};
                break;
            case RANDOM:
                imageList = Objects.requireNonNull(extraData.getRandomAvatar()).invoke();
                break;
        }
    }

    private void setPos(JsonArray posElements, Type imageType) {
        int i = 0;
        switch (posType) {
            case MARGIN:
            case ZOOM:
                switch (imageType) {
                    case GIF:
                        pos = new int[posElements.size()][4];
                        for (JsonElement je : posElements) {
                            JsonArray ja = (JsonArray) je;
                            if (ja.size() != 4) {
                                return;
                            }
                            pos[i++] = JsonArrayToIntArray(ja);
                        }
                        break;
                    case IMG:
                        pos[i] = JsonArrayToIntArray(posElements);
                        break;
                }
                break;
            case DEFORM:
                switch (imageType) {
                    case GIF:
                        try {
                            deformData = DeformData.fromGifPos(posElements);
                            break;
                        } catch (Exception ignored) {
                        }
                    case IMG:
                        deformData = DeformData.fromImgPos(posElements);
                        break;
                }
                break;
        }
    }

    private void setCrop(JsonArray crop) {
        if (crop == null || crop.isEmpty()) return;
        int[] result = JsonArrayToIntArray(crop);
        cropPos = result.length == 2 ? new int[]{0, 0, result[0], result[1]} : result;
    }

    private int[] JsonArrayToIntArray(JsonArray ja) {
        int[] result = new int[ja.size()];
        short i = 0;
        for (JsonElement je : ja) {
            String str = je.toString().replace("\"", "");
            try {
                result[i] = Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
                ArithmeticParser parser = new ArithmeticParser(str);
                parser.put("width", this.getImageWidth());
                parser.put("height", this.getImageHeight());
                result[i] = (int) parser.eval();
            }
            i++;
        }
        return result;
    }

    private void buildImage() {
        if (cropType != CropType.NONE) imageList = ImageSynthesis.cropImage(imageList, cropType, cropPos);

        for (AvatarStyle style : styleList) {
            switch (style) {
                case FLIP:
                    imageList = ImageSynthesis.flipImage(imageList);
                    break;
                case MIRROR:
                    imageList = ImageSynthesis.mirrorImage(imageList);
                    break;
                case GRAY:
                    imageList = ImageSynthesis.grayImage(imageList);
                    break;
                case BINARIZATION:
                    imageList = ImageSynthesis.binarizeImage(imageList);
                    break;
            }
        }

        if (round) {
            imageList = ImageSynthesis.convertCircular(imageList, antialias);
        }

        if (resampling && posType == AvatarPosType.ZOOM) {
//            int tw = 0, th = 0, ti = 0;
//            for (int[] p : pos) {
//                if (p[2] == 0 || p[3] == 0) continue;
//                tw += p[2];
//                th += p[3];
//                ti++;
//            }
//            int aw = tw / ti, ah = th / ti;
            int aw = 0, ah = 0;
            for (int[] p : pos) {
                if (p[2] > aw) aw = p[2];
                if (p[3] > ah) ah = p[3];
            }

            imageList = new ArrayList<>(imageList);
            if (imageList.size() == 1) {
                try {
                    imageList.set(0, Thumbnails.of(imageList.get(0)).size(aw, ah).keepAspectRatio(false).asBufferedImage());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                return;
            }

            final int faw = aw, fah = ah;
            CountDownLatch latch = new CountDownLatch(imageList.size());
            for (short i = 0; i < imageList.size(); i++) {
                short fi = i;
                threadPool.execute(() -> {
                    try {
                        var img = Thumbnails.of(imageList.get(fi)).size(faw, fah).keepAspectRatio(false).asBufferedImage();
                        imageList.set(fi, img);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public FitType getZoomType() {
        return fitType;
    }
    public List<Position> getPosition() {
        return position;
    }

    public float getOpacity() {
        return opacity;
    }

    public boolean isRound() {
        return round;
    }

    public boolean isRotate() {
        return rotate;
    }

    public boolean isOnTop() {
        return onTop;
    }

    public boolean isAntialias() {
        return antialias;
    }

    /**
     * 获取已经构建好的图像, 请放心食用
     */
    public List<BufferedImage> getImageList() {
        return imageList;
    }

    public BufferedImage getFirstImage() {
        return imageList.get(0);
    }

    /**
     * 获取下一个旋转角度
     * <li>不旋转时 返回初始角度</li>
     * <li>IMG格式 返回随机角度</li>
     * <li>GIF 返回下一个旋转角度</li>
     *
     * @deprecated 应当使用index直接获取角度, 之后的版本将不再维护posIndex
     */
    @Deprecated
    public float getNextAngle() {
        if (!rotate) return angle; //不旋转
        if (imageType == Type.IMG) return new Random().nextInt(angle != 0 ? angle : 360); //IMG随机旋转
        return ((float) (360 / pos.length) * posIndex) + angle; //GIF自动旋转
    }

    /**
     * 获取旋转角度
     * <li>不旋转时 返回初始角度</li>
     * <li>IMG格式 返回随机角度</li>
     * <li>GIF 返回旋转角度</li>
     */
    public float getAngle(short index) {
        if (!rotate) return angle; //不旋转
        if (imageType == Type.IMG) return new Random().nextInt(angle != 0 ? angle : 360); //IMG随机旋转
        return ((float) (360 / pos.length) * index) + angle; //GIF自动旋转
    }

    /**
     * 获取下一个坐标
     *
     * @deprecated 应当使用index直接获取坐标, 之后的版本将不再维护posIndex
     */
    @Deprecated
    public int[] nextPos() {
        return getPos(posIndex++);
    }

    /**
     * 获取坐标(索引越界会返回最后的坐标)
     */
    public int[] getPos(short i) {
        if (i >= pos.length) return pos[pos.length - 1];
        return pos[i];
    }

    public AvatarPosType getPosType() {
        return posType;
    }

    /**
     * 获取坐标数组实际长度
     */
    public short getPosLength() {
        return (short) pos.length;
    }

    public DeformData getDeformData() {
        return deformData;
    }

    public static class DeformData {
        static final int POS_SIZE = 4;
        Point2D[][] deformPos;
        int[][] anchor;

        public static DeformData fromGifPos(JsonArray posElements) {
//            System.out.println("DeformData fromPos by: " + posElements.toString());

            DeformData deformData = new DeformData();
            deformData.deformPos = new Point2D[posElements.size()][POS_SIZE];
            deformData.anchor = new int[posElements.size()][2];
            short f = 0;
            for (JsonElement frame : posElements) {
                for (short i = 0; i < POS_SIZE; i++) {
                    deformData.deformPos[f][i] = new Point2D.Double(
                            Integer.parseInt(((JsonArray) ((JsonArray) frame).get(i)).get(0).toString()),
                            Integer.parseInt(((JsonArray) ((JsonArray) frame).get(i)).get(1).toString())
                    );
                }
                deformData.anchor[f][0] = Integer.parseInt(((JsonArray) ((JsonArray) frame).get(POS_SIZE)).get(0).toString());
                deformData.anchor[f][1] = Integer.parseInt(((JsonArray) ((JsonArray) frame).get(POS_SIZE)).get(1).toString());
                f++;
            }
            return deformData;
        }

        public static DeformData fromImgPos(JsonArray posElements) {
            DeformData deformData = new DeformData();
            deformData.deformPos = new Point2D[1][POS_SIZE];
            deformData.anchor = new int[1][2];
            for (short i = 0; i < POS_SIZE; i++) {
                deformData.deformPos[0][i] = new Point2D.Double(
                        Integer.parseInt(((JsonArray) posElements.get(i)).get(0).toString()),
                        Integer.parseInt(((JsonArray) posElements.get(i)).get(1).toString())
                );
            }
            deformData.anchor[0][0] = Integer.parseInt(((JsonArray) posElements.get(POS_SIZE)).get(0).toString());
            deformData.anchor[0][1] = Integer.parseInt(((JsonArray) posElements.get(POS_SIZE)).get(1).toString());

            return deformData;
        }

        public short getLength() {
            return (short) deformPos.length;
        }

        public Point2D[] getDeformPos(short i) {
            i = (short) (i % deformPos.length);
            return deformPos[i];
        }

        public int[] getAnchor(short i) {
            i = (short) (i % anchor.length);
            return anchor[i];
        }
    }

    public int getImageWidth() {
        return this.getFirstImage().getWidth();
    }

    public int getImageHeight() {
        return this.getFirstImage().getHeight();
    }

    public boolean isGif() {
        return imageList.size() > 1;
    }

    /**
     * 获取头像下一帧, 超过索引长度会重新开始循环 <b>(线程不安全)</b>
     * <b>应当使用index直接获取帧</b>
     */
    public BufferedImage nextFrame() {
        if (frameIndex >= imageList.size()) frameIndex = 0;
        return imageList.get(frameIndex++);
    }

    /**
     * 获取指定帧数, 超过索引长度会从头计数
     * 例如: length: 8 index: 10 return: list[1]
     */
    public BufferedImage getFrame(short i) {
        i = (short) (i % imageList.size());
        return imageList.get(i);
    }
}