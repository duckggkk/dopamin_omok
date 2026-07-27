2026-07-27

`JwtChannelInterceptor`의 `preSend()`를 검토하다가 `return message`, `return null`, 예외 발생이 각각 무엇을 의미하는지 혼동했다. 각 return 값들이 뭘 의미하는지 정리해보았다.

이번에는 `ChannelInterceptor`의 위치와 반환값을 기준으로 Spring WebSocket/STOMP의 메시지 흐름을 정리했다.


********************************************************************************

### 1. `preSend()`는 연결 요청만 검사하는 메서드가 아니다

현재 프로젝트는 `JwtChannelInterceptor`를 `clientInboundChannel`에 등록한다.

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(jwtChannelInterceptor);
}
```

따라서 `preSend()`는 최초 WebSocket 연결에서 한 번만 실행되는 필터가 아니다. 클라이언트가 보낸 STOMP 메시지가 `clientInboundChannel`로 전달될 때마다 실행된다.

대표적인 STOMP 명령은 다음과 같다.

- `CONNECT`: STOMP 세션 연결
- `SUBSCRIBE`: 목적지 구독
- `SEND`: 애플리케이션 또는 브로커 목적지로 메시지 전송
- `UNSUBSCRIBE`: 구독 해제
- `DISCONNECT`: STOMP 세션 종료

HTTP에서 서블릿 필터가 요청을 컨트롤러보다 먼저 검사하는 것과 비슷하지만, `ChannelInterceptor`가 검사하는 대상은 HTTP 요청이 아니라 Spring Messaging의 `Message<?>`다.

즉, `preSend()`의 반환값은 “WebSocket 연결 전체를 허용하는가”가 아니라 “현재 메시지 한 개를 채널로 계속 보낼 것인가”를 결정한다.


********************************************************************************

### 2. `return message`, `return null`, 예외의 차이

`ChannelInterceptor.preSend()`의 반환값은 다음과 같은 의미를 가진다.

```java
return message;  // 메시지를 그대로 다음 채널 처리 단계로 전달
return changed;  // 변경한 메시지를 다음 채널 처리 단계로 전달
return null;     // 현재 메시지를 채널로 보내지 않고 폐기
throw exception; // 현재 메시지 처리를 오류로 실패
```

#### `return message`

현재 인터셉터가 메시지를 막거나 바꾸지 않았다는 뜻이다. 다음 인터셉터가 있다면 그 인터셉터로 이동하고, 더 없다면 `clientInboundChannel`을 구독한 Spring의 메시지 핸들러로 전달된다.

이 프로젝트에는 직접 등록한 다음 인바운드 인터셉터가 없으므로, `JwtChannelInterceptor`를 통과한 메시지는 Spring의 메시지 라우팅 단계로 이동한다.

#### `return null`

메시지 전송 자체가 수행되지 않는다. 예외를 발생시키지 않고 조용히 버리는 방식이다.

인증 실패처럼 클라이언트가 실패 사실을 알아야 하는 상황에서 `null`을 반환하면 원인을 알기 어려울 수 있다. 클라이언트는 응답을 기다리다가 타임아웃이 발생한 것으로 볼 수도 있다.

#### 예외 발생

현재 메시지를 명시적으로 실패시킨다.

현재 `JwtChannelInterceptor`는 JWT 인증 실패, 허용하지 않은 destination, 방 멤버십 부족 등을 `MessagingException`으로 거부한다. 인증·인가 실패는 조용히 폐기하기보다 예외로 중단하는 편이 의도가 분명하다.



********************************************************************************

### 3. STOMP 명령이 없는 heartbeat를 통과시키면 실제로 무엇이 일어나는가

가장 궁금한건 STOMP 명령이 없으면 무엇인가? 였고 답은 간단했다. 웹 소켓의 생존 확인을 위한 heartbeat였다.

```java
if (accessor == null || accessor.getCommand() == null) {
    return message;
}
```

STOMP heartbeat는 보통 개행 문자 하나로 전송되며 `CONNECT`, `SEND` 같은 STOMP 명령을 가지지 않는다. Spring은 이를 `SimpMessageType.HEARTBEAT` 메시지로 변환한다.

이 메시지가 `return message`로 통과하면 현재 프로젝트에서 사용하는 `SimpleBrokerMessageHandler`까지 전달된다.

실제 후속 처리는 다음과 같다.

```text
HEARTBEAT 수신
  ↓
SimpleBrokerMessageHandler.updateSessionReadTime(sessionId)
  ↓
해당 세션의 마지막 수신 시각 갱신
  ↓
추가 처리 없이 종료
```

heartbeat는 `MESSAGE`, `CONNECT`, `DISCONNECT`, `SUBSCRIBE`, `UNSUBSCRIBE` 중 어느 타입도 아니므로 컨트롤러 호출이나 브로드캐스트가 발생하지 않는다.

결과적으로 heartbeat를 통과시켰을 때 발생하는 일은 연결 생존 확인을 위한 세션 수신 시각 갱신뿐이다.

- 새로운 STOMP 세션을 만들지 않는다.
- 인증 사용자를 만들지 않는다.
- `@MessageMapping`을 호출하지 않는다.
- `/topic` 구독자에게 메시지를 보내지 않는다.
- DB를 변경하지 않는다.

아직 정상적인 `CONNECT`를 완료하지 않은 클라이언트가 heartbeat만 보내더라도 인증된 STOMP 세션이 만들어지지 않는다. 등록된 브로커 세션이 없으므로 수신 시각 갱신도 사실상 아무 효과가 없다.


********************************************************************************

### 5. 기존 주석에서 잘못 이해한 부분

처음에는 다음과 같이 이해했다.

```java
// 다음 인터셉터에서 판단한다.
return message;
```

그러나 현재 프로젝트에는 다음 커스텀 인바운드 인터셉터가 없다. `return message`는 특정한 “다음 인터셉터”에게 판단을 위임한다는 뜻이 아니라, 현재 메시지를 `clientInboundChannel`의 후속 처리로 계속 전달한다는 뜻이다.

heartbeat에 한정하면 더 구체적으로 다음과 같이 설명할 수 있다.

```java
// heartbeat는 STOMP command가 없다.
// 그대로 통과시키면 SimpleBroker가 세션의 마지막 수신 시각을 갱신하고,
// 컨트롤러 호출이나 메시지 방송 없이 처리를 끝낸다.
if (accessor == null || accessor.getCommand() == null) {
    return message;
}
```

다만 `accessor == null`과 `command == null`은 완전히 같은 경우는 아니다.

- `command == null`: heartbeat처럼 STOMP 명령이 없는 메시지일 수 있다.
- `accessor == null`: 메시지에서 `StompHeaderAccessor`를 가져올 수 없는 경우다.

정상적인 외부 STOMP 프레임은 디코딩 과정에서 STOMP 접근자를 가지므로, `accessor == null`은 일반적인 클라이언트 행동 요청 경로가 아니다. 향후 이 분기를 변경한다면 두 경우를 하나의 설명으로 뭉뚱그리지 말고 각각 어떤 메시지가 들어올 수 있는지 확인해야 한다.


********************************************************************************

### 정리

이번 궁금증은 각 return값의 의미를 알아보고자 시작했다. 


1. `return message`는 거부가 아니라 현재 메시지의 후속 처리를 허용한다.
2. `return null`은 예외 없이 현재 메시지를 폐기한다.
3. 예외 발생은 현재 메시지를 명시적으로 실패시킨다.
4. 후속 처리는 메시지 타입과 destination에 따라 컨트롤러 또는 메시지 브로커가 담당한다.
5. heartbeat를 통과시키면 SimpleBroker가 세션의 마지막 수신 시각만 갱신하고 종료한다.
6. heartbeat 통과는 인증이나 STOMP 연결 성립을 의미하지 않는다.
7. 인증·인가 실패는 조용한 폐기보다 예외로 거부하는 편이 문제 원인을 명확히 드러낸다.

메서드의 반환형이 `Message<?>`라는 점을 먼저 보았다면 연결 성공 여부를 반환한다고 오해하지 않았을 것이다. 프레임워크 코드를 읽을 때는 메서드 이름뿐 아니라 반환 타입, 호출 위치, 반환값을 소비하는 다음 객체까지 함께 확인해야 한다.
