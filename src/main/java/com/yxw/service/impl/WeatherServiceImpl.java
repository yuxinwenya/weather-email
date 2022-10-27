package com.yxw.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxw.entity.Meteorological;
import com.yxw.entity.Weather;
import com.yxw.mapper.WeatherMapper;
import com.yxw.service.WeatherService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>
 * 天气表 服务实现类
 * </p>
 *
 * @author 余欣文
 * @since 2022-10-12
 */
@Log4j2
@Service
@Data
public class WeatherServiceImpl extends ServiceImpl<WeatherMapper, Weather> implements WeatherService {
    @Value("${request.weather.qq}")
    private String FROM_MAIL;
    @Value("${request.weather.addpid}")
    private String appId;
    @Value("${request.weather.appsecret}")
    private String appSecret;
    private final static String WEA = "雨";

    @Resource
    private WeatherService service;
    @Resource
    private JavaMailSender emailSender;
    Object city;
    List<Weather> list = null;

    /**
     * 发送简单信息
     *
     * @return boolean
     */
    @Override
    public boolean sendSimpleMessage() {
        QueryWrapper<Weather> wrapper = new QueryWrapper<>();
        wrapper.eq("is_delete", 1);
        list = service.list(wrapper);
        for (Weather weather : list) {
            this.getWeather(weather);
            send(weather);
            log.info(weather.getName() + "邮件已发送");
        }
        return true;

    }

    private void send(Weather weather) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(message, true);
            mimeMessageHelper.setTo(weather.getMail());
            mimeMessageHelper.setFrom(FROM_MAIL);
            mimeMessageHelper.setSubject(weather.getTitle());
            mimeMessageHelper.setText(buildHtml(getWeather(weather).get(0), weather), true);
            emailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Meteorological> getWeather(Weather list) {
        HttpRequest httpRequest = HttpUtil.createGet("https://www.tianqiapi.com/api?version=v1&" + "appid=" + appId + "&appsecret=" + appSecret + "&cityid=" + list.getCityId());
        String res = httpRequest.execute().body();
        Object data = JSON.parseObject(res).get("data");
        city = JSON.parseObject(res).get("city");
        return JSON.parseArray(JSON.toJSONString(data), Meteorological.class);
    }


    private String buildHtml(Meteorological meteorological, Weather list) {
        StringBuffer html = new StringBuffer();
        String airTips = meteorological.getAirTips();
        html.append("<!DOCTYPE html>\n" + "<html>\n" + "<head>\n" + "<meta charset=\"utf-8\">\n" + "<title>文档标题</title>\n" + "</head><body>");
        html.append("<div class=\"table-wrapper\">");
        if (meteorological.getWea().contains(WEA)) {
            html.append("<h1>").append(list.getPrompt()).append("</h1>");
        }
        if (meteorological.getWea().contains("")) {
            html.append("<h1 style=\"color: red;\">").append(city).append("今日").append(meteorological.getWea()).append("，").append(airTips).append("</h1>");
        } else {
            html.append("<h1>").append(city).append("今日").append(meteorological.getWea()).append("</h1>");
        }
        Object alarmtype = meteorological.getAlarm().get("alarmtype");
        if (!Objects.isNull(alarmtype)) {
            html.append("预警类型：<span style=\"color: red;\">").append(meteorological.getAlarm().get("alarmtype")).append("</span>&nbsp&nbsp");
            html.append("预警等级：<span style=\"color: red;\">").append(meteorological.getAlarm().get("alarmcontent")).append("</span>&nbsp&nbsp<br>");
            html.append("预警详情描述：<span style=\"color: red;\">").append(meteorological.getAlarm().get("alarmlevel")).append("</span>&nbsp&nbsp<br>");
        }
        html.append("最高温度：<span style=\"color: violet;\">").append(meteorological.getTem1()).append("</span>&nbsp&nbsp");
        html.append("最低温度：<span style=\"color:red;\">").append(meteorological.getTem2()).append("</span>&nbsp&nbsp");
        html.append("当前温度：<span style=\"color:green;\">").append(meteorological.getTem()).append("</span><br>");
        html.append("白天天气：<span style=\"color: #0000FF;\">").append(meteorological.getWeaDay()).append("</span>&nbsp&nbsp");
        html.append("夜间天气：<span style=\"color: #0000FF;\">").append(meteorological.getWeaNight()).append("</span>&nbsp&nbsp");
        html.append("空气湿度：<span style=\"color: color:#5C3317;\">").append(meteorological.getHumidity()).append("</span>&nbsp&nbsp");
        html.append("浓见度：<span style=\"color: #0000FF;\">").append(meteorological.getVisibility()).append("</span>&nbsp&nbsp");
        html.append("<table class=\"fl-table\"><tr><th>名称标题</th><th>强度</th><th>今日建议</th></tr>");
        Optional.ofNullable(meteorological.getIndex()).orElse(new ArrayList<>()).forEach(whours -> html.append("<tr><td>").append(whours.getTitle()).append("</td><td>").append(whours.getLevel()).append("</td><td>").append(whours.getDesc()).append("</td><td>").append("</td><td>").append("</td></tr>"));
        html.append("</table></body>" + "</html>");
        return html.toString();
    }
}