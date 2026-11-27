package com.sunyun.anygo;

import org.junit.Test;

import com.sunyun.anygo.utils.MapUtils;

import static org.junit.Assert.assertTrue;

/**
 * 坐标转换单元测试（虚拟定位的精度命脉，改动 MapUtils 必须跑一遍）
 */
public class MapUtilsTest {

    /** 参考点：北京天安门附近。GCJ 参考值为本算法输出（与公开换算表一致到 ~1e-4 度） */
    private static final double WGS_LNG = 116.39122879;
    private static final double WGS_LAT = 39.90733345;
    private static final double GCJ_LNG = 116.39746991;
    private static final double GCJ_LAT = 39.90873468;
    private static final double TOLERANCE = 5e-4;

    private static void assertClose(String msg, double expected, double actual) {
        assertTrue(String.format("%s 期望 %f, 实际 %f (偏差 %e)", msg, expected, actual, Math.abs(expected - actual)),
                Math.abs(expected - actual) < TOLERANCE);
    }

    @Test
    public void wgs2gcj02_referencePoint() {
        double[] gcj = MapUtils.wgs2gcj02(WGS_LNG, WGS_LAT);
        assertClose("wgs2gcj02 lng", GCJ_LNG, gcj[0]);
        assertClose("wgs2gcj02 lat", GCJ_LAT, gcj[1]);
    }

    @Test
    public void gcj2wgs_roundtrip() {
        double[] back = MapUtils.gcj2wgs(GCJ_LNG, GCJ_LAT);
        // 反演是近似算法，允许 1e-4 度（≈10m）误差
        assertTrue(Math.abs(back[0] - WGS_LNG) < 1e-4);
        assertTrue(Math.abs(back[1] - WGS_LAT) < 1e-4);
    }

    @Test
    public void wgs2gcj02_gcj2wgs_roundtrip() {
        double[] gcj = MapUtils.wgs2gcj02(WGS_LNG, WGS_LAT);
        double[] back = MapUtils.gcj2wgs(gcj[0], gcj[1]);
        assertTrue(Math.abs(back[0] - WGS_LNG) < 1e-4);
        assertTrue(Math.abs(back[1] - WGS_LAT) < 1e-4);
    }

    @Test
    public void wgs2bd09_gcj_roundtrip() {
        // BD09 <-> GCJ 往返经 sin/cos 近似，固有误差 ~5e-7 度
        double[] bd = MapUtils.wgs2bd09(WGS_LNG, WGS_LAT);
        double[] back = MapUtils.bd09togcj02(bd[0], bd[1]);
        assertTrue(Math.abs(back[0] - GCJ_LNG) < 1e-6);
        assertTrue(Math.abs(back[1] - GCJ_LAT) < 1e-6);
    }

    @Test
    public void bd2wgs_roundtrip() {
        double[] wgs = MapUtils.bd2wgs(WGS_LNG, WGS_LAT);
        double[] back = MapUtils.wgs2bd09(wgs[0], wgs[1]);
        assertTrue(Math.abs(back[0] - WGS_LNG) < 1e-4);
        assertTrue(Math.abs(back[1] - WGS_LAT) < 1e-4);
    }

    @Test
    public void gcj_offset_is_plausible() {
        // 国内偏移量应在数百米量级（0.001 ~ 0.01 度），过大/为零都说明算法坏了
        double[] gcj = MapUtils.wgs2gcj02(WGS_LNG, WGS_LAT);
        double dLng = Math.abs(gcj[0] - WGS_LNG);
        double dLat = Math.abs(gcj[1] - WGS_LAT);
        assertTrue("经度偏移异常: " + dLng, dLng > 0.001 && dLng < 0.01);
        assertTrue("纬度偏移异常: " + dLat, dLat > 0.001 && dLat < 0.01);
    }
}
