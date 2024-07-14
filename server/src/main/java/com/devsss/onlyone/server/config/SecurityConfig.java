package com.devsss.onlyone.server.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class SecurityConfig {

    @Value("${onlyone.admin.user}")
    private String userName;

    @Value("${onlyone.admin.password}")
    private String password;

    @Bean
    public KeyPair createKeyPair() throws NoSuchAlgorithmException {
        // KeyPairGenerator类用于生成公钥和私钥对，基于RSA算法生成对象
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        // 初始化密钥对生成器
        keyPairGen.initialize(2048, new SecureRandom());
        // 生成一个密钥对，保存在keyPair中
        return keyPairGen.generateKeyPair();
    }

    @Bean
    SecurityFilterChain web(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorize) ->
                        authorize.requestMatchers(
                                "/login","/jwtLogin","/css/**","/js/**","/favicon.ico","/image/**"
                                ,"/","/lj","/zs").permitAll()
                                .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login"))
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer( c -> {
                    c.jwt(Customizer.withDefaults());
                })
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                );
        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager inMemoryUserDetailsManager() {
        return new InMemoryUserDetailsManager(User.withUsername(userName).password(password).roles("USER").build());
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair keyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair keyPair) {
        JWK jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey(keyPair.getPrivate()).build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }
}
