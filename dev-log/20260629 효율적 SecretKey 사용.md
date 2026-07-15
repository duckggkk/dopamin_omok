2026-06-29

********************************************************************************

### 1. JwtProvider — 지연 초기화(이중 검사 잠금)를 생성자 final로


#### 의문점
- `JwtProvider`에서 `volatile` + 이중 검사 잠금(Double-Checked Locking)으로 `SecretKey`를 캐싱하고 있었다.
- "지연 초기화 + 이중 검사 잠금" 패턴으로 스레드 안전하게 무언가를 한 번만 만들고 싶을 때 쓰는 패턴이다.
- 사실상 무조건 사용하게 될 `SecretKey` 특성상 캐싱 대신 싱글톤 패턴의 객체 생성(서버 기동) 시점 초기화가 옳다고 판단했다.

#### 당시코드
`SecretKey`를 첫 요청 때 만들고(지연), 여러 스레드가 동시에 만들지 못하게 `volatile` + `synchronized` 이중 검사로 막고 있었다.
```java
// Before
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    // SecretKey를 한 번만 생성해 재사용 (thread-safe)
    private volatile SecretKey cachedKey;

    private SecretKey getSigningKey() {
        if (cachedKey == null) {                 // ① 락 없이 1차 검사
            synchronized (this) {                // ② 한 명만 진입
                if (cachedKey == null) {         // ③ 안에서 다시 검사
                    byte[] keyBytes = jwtProperties.getSecret()
                            .getBytes(StandardCharsets.UTF_8);
                    cachedKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
        }
        return cachedKey;
    }
    // ... 토큰 생성/검증에서 매번 getSigningKey() 호출
}
```

#### 해결법
처음엔 `static final`을 떠올렸지만 그건 불가능했다.
- `static`은 클래스 로딩 시점에 값이 정해지는데, 그땐 `secret`이 아직 없다(yml -> 스프링이 한참 뒤에 주입).
- `static`(클래스 소속)에서 `jwtProperties`(인스턴스 소속) 같은 인스턴스 필드를 참조할 수도 없다.

핵심은 `JwtProvider`가 스프링이 만드는 싱글톤이라는 점이었다. 이 객체는 서버에 딱 하나만 생기고, 생성자가 호출되는 시점엔 이미 `jwtProperties`가 주입돼 있어 `secret`을 쓸 수 있다. 즉 "객체 만들 때 키를 한 번 만들어 `final` 필드에 박아두면" 그게 곧 서버 전체에서 한 번만 만드는 것이 된다.




그래서 `static`이 아닌 `final` 인스턴스 필드 + 생성자 계산으로 바꿨다. 그러자 `volatile`·`synchronized`·이중 검사·`getSigningKey()`가 통째로 사라졌다.
```java
// After
@Slf4j
@Component
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;   // final → 한 번 정해지면 끝, 스레드 안전 보장

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
    // ... 이제 signingKey 필드를 바로 사용
}

```
- 주의: 생성자를 직접 작성하면서 @RequiredArgsConstructor는 제거해야 했다. 그대로 두면 Lombok이 final 필드 전체(jwtProperties, signingKey)를 받는 생성자를 추가로 만들 수 있고, 이 생성자가 선택될 경우 Spring은 SecretKey까지 빈으로 주입하려고 한다. 하지만 SecretKey는 생성자 내부에서 계산해야 하는 값이지 별도 빈이 아니므로 빈 생성 실패로 이어질 수 있다.



#### 알게 된 것과 변경 후 이점
- `더 나은 선택`. 기존 코드도 잘 작동한다. 다만 이 상황(스프링 싱글톤 + 생성자에 의존성이 이미 들어옴)에선 더 단순한 선택지가 있었을 뿐이다.
- `스레드 안전 보장` 자바는 "생성자가 끝나면 모든 스레드가 `final` 필드의 최신 값을 안전하게 본다"고 언어 차원에서 보장한다(safe publication). `volatile`이 하던 일을 `final`이 대신해줬다.
- `안정적 패턴` 키 생성이 "첫 요청 시점"에서 "서버 기동 시점"으로 옮겨졌다. `secret`이 잘못되면(비었거나 256비트 미만) 서버가 부팅하며 `WeakKeyException`으로 일찍 죽는다(fail-fast).
- `호출 비용 감소` 기존엔 토큰을 만들 때마다 `null` 검사 + `volatile` 읽기를 했지만, 이제는 이미 박혀 있는 필드를 읽기만 한다.




