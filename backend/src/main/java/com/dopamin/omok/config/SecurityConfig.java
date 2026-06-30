package com.dopamin.omok.config;

import com.dopamin.omok.global.security.jwt.JwtAuthenticationFilter;
import com.dopamin.omok.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;  // CorsConfig 빈 명시적 주입

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CORS — CorsConfig의 설정을 Spring Security 필터 체인에 명시적으로 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // CSRF — JWT Stateless 방식이므로 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 미사용
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 보안 헤더
                .headers(headers -> headers
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions ->
                                permissions.policy("camera=(), microphone=(), geolocation=()"))
                )

                // 엔드포인트 권한
                .authorizeHttpRequests(auth -> auth
                        // 로그아웃은 로그인 사용자만. /auth/** permitAll 보다 먼저 두어 우선 적용한다.
                        // (미인증 호출 시 컨트롤러에서 principal=null → NPE → 500 이 나던 것을 401 로 깔끔히 거부)
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // OpenAPI / Swagger UI (springdoc) — context-path(/api) 하위에서 노출
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Prometheus 메트릭: 외부 접근은 Caddy에서 차단하고, 백엔드 포트(8080)는
                        // 외부에 노출되지 않으므로 내부망의 Prometheus 컨테이너만 직접 수집한다.
                        .requestMatchers("/actuator/prometheus").permitAll()

                        // ── 멤버(정식 회원) 전용 ──
                        // 게스트(GUEST)는 토큰은 있어(=인증됨) anyRequest().authenticated() 를 통과하므로,
                        // 멤버 전용 기능은 여기서 역할(USER/ADMIN)로 명시적으로 막아야 한다.
                        // (게스트 허용: 싱글 AI /ai/**, 조회성 GET 등. 게스트 차단: 아래 경로)
                        // 상점·친구는 멤버 전용. 광장(/plaza)은 게스트도 입장 가능(아바타 꾸미기만 WS에서 차단).
                        .requestMatchers("/shop/**", "/friends/**").hasAnyRole(UserRole.USER.name(), UserRole.ADMIN.name())
                        // 방(/rooms)은 URL 일괄 차단하지 않는다 — 게스트도 '캐주얼' 방은 생성·참가할 수 있어야 하기 때문.
                        // 랭크 방 생성/참가 차단은 RoomService 가 역할 기반으로 선별 처리한다(게스트=캐주얼 강제, 랭크 참가 거부).
                        // 프로필 수정(닉네임/이미지 등)은 멤버만.
                        .requestMatchers(HttpMethod.PATCH, "/users/me").hasAnyRole(UserRole.USER.name(), UserRole.ADMIN.name())

                        // 그 외 모든 요청은 인증 필요. 게임/방 조회도 로그인 후에만 접근(프론트 동작과 일치).
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
