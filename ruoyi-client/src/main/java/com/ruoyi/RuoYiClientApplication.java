package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 终端服务启动程序
 * <p>
 * 与管理后台入口 {@link RuoYiApplication} 相互独立，两者各自是一个 Spring Boot 进程。
 * 扫描的根包同为 com.ruoyi，因此 ruoyi-framework、ruoyi-system 中的 Bean 均会被加载。
 *
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class RuoYiClientApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiClientApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  终端服务启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}
