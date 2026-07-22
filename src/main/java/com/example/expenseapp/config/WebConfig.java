package com.example.expenseapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Webレイヤーの横断設定をまとめたクラス。
 * ・CORS設定（React開発サーバーからのAPIアクセス許可）
 * ・領収書画像（/uploads配下）の静的リソース公開設定
 * の2つをここに集約する。
 *
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	/**
     * /uploads/xxx.jpg のようなURLで、サーバー上の uploads/ ディレクトリ内の
     * ファイルにブラウザから直接アクセスできるようにする。
     * これがないと、アップロードした領収書画像は保存はされてもプレビュー表示ができない。
     */
	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
	
	// allowCredentials(true)を追加し、Cookie（セッションCookie・CSRFトークン）の
	// やり取りを許可する。これが無いと、ブラウザがwithCredentials付きのリクエストを
	// 送った際に、サーバー側からの応答が拒否されてしまう
	@Override
	public void addCorsMappings(CorsRegistry registry) {
	    registry.addMapping("/api/**")
	            .allowedOrigins("http://localhost:5173")
	            .allowedMethods("GET", "POST", "PUT", "DELETE")
	            .allowedHeaders("*")
	            .allowCredentials(true);
	}
}
