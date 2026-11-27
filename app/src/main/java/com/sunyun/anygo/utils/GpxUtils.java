package com.sunyun.anygo.utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 GPX 1.1 读写：解析 <trkpt>/<rtept> 的经纬度序列，导出为 track。
 * 坐标一律 WGS-84（GPX 标准），GCJ-02 转换由调用方完成。
 */
public class GpxUtils {

    public static class GpxTrack {
        public String name = "";
        public final List<double[]> points = new ArrayList<>();   // {lng, lat} WGS-84
    }

    /** 解析 GPX 流中的轨迹/路线点（WGS-84）。找不到点时返回空列表。 */
    public static GpxTrack parse(InputStream in) throws Exception {
        GpxTrack track = new GpxTrack();
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(in, null);

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("name".equals(tag) && track.name.isEmpty()) {
                    track.name = parser.nextText().trim();
                } else if ("trkpt".equals(tag) || "rtept".equals(tag) || "wpt".equals(tag)) {
                    String lat = parser.getAttributeValue(null, "lat");
                    String lon = parser.getAttributeValue(null, "lon");
                    if (lat != null && lon != null) {
                        try {
                            track.points.add(new double[]{Double.parseDouble(lon), Double.parseDouble(lat)});
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            event = parser.next();
        }
        return track;
    }

    /** 导出 GPX 1.1 文本（track + trkpt 序列，WGS-84） */
    public static String build(String name, List<double[]> wgsPoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"AnyGo\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <trk>\n");
        sb.append("    <name>").append(escape(name)).append("</name>\n");
        sb.append("    <trkseg>\n");
        for (double[] pt : wgsPoints) {
            sb.append(String.format("      <trkpt lat=\"%.8f\" lon=\"%.8f\" />\n", pt[1], pt[0]));
        }
        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
