import { useEffect, useRef, useCallback, MutableRefObject } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import { tokenStorage } from '@/utils/token';
import { ApiResponse, ChatMessage, Direction, PlazaAppearance, PlazaInputType, PlazaSnapshot } from '@/types';

interface UsePlazaSocketOptions {
  channelId: string | null; // REST 배정 전엔 null → 연결 보류
  appearanceRef: MutableRefObject<PlazaAppearance>; // 입장(및 재연결) 시 보낼 최신 외형
  onSnapshot: (snap: PlazaSnapshot) => void;
  onChat: (msg: ApiResponse<ChatMessage>) => void;
}

const parse = <T>(m: IMessage): T => JSON.parse(m.body) as T;

/**
 * 광장 전용 STOMP 연결. channelId 가 정해진 뒤 연결하고, (재)연결 시 토픽 구독 + 입장(join)을 자동 수행한다.
 * 게임 방용 useWebSocket 과 분리(광장은 방 개념이 없음).
 */
export const usePlazaSocket = ({ channelId, appearanceRef, onSnapshot, onChat }: UsePlazaSocketOptions) => {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    if (!channelId) return;
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      beforeConnect: () => {
        client.connectHeaders = { Authorization: `Bearer ${tokenStorage.getAccessToken() ?? ''}` };
      },
      onConnect: () => {
        client.subscribe(`/topic/plaza/${channelId}`, (m: IMessage) => onSnapshot(parse<PlazaSnapshot>(m)));
        client.subscribe(`/topic/plaza/${channelId}/chat`, (m: IMessage) => onChat(parse<ApiResponse<ChatMessage>>(m)));
        // 입장(스폰) — 현재 외형을 함께 전달
        client.publish({
          destination: `/app/plaza/${channelId}/join`,
          body: JSON.stringify({ appearance: appearanceRef.current }),
        });
      },
      onStompError: (frame) => console.error('STOMP error', frame),
    });

    client.activate();
    clientRef.current = client;
  }, [channelId, appearanceRef, onSnapshot, onChat]);

  const disconnect = useCallback(() => {
    const client = clientRef.current;
    if (client?.connected && channelId) {
      try {
        client.publish({ destination: `/app/plaza/${channelId}/leave`, body: '{}' });
      } catch {
        /* 종료 중 — 무시 */
      }
    }
    client?.deactivate();
  }, [channelId]);

  const sendInput = useCallback(
    (type: PlazaInputType, direction?: Direction) => {
      if (!clientRef.current?.connected || !channelId) return;
      clientRef.current.publish({
        destination: `/app/plaza/${channelId}/input`,
        body: JSON.stringify({ type, direction: direction ?? null }),
      });
    },
    [channelId],
  );

  const sendChat = useCallback(
    (content: string) => {
      if (!clientRef.current?.connected || !channelId) return;
      clientRef.current.publish({ destination: `/app/plaza/${channelId}/chat`, body: JSON.stringify({ content }) });
    },
    [channelId],
  );

  const sendAppearance = useCallback(
    (appearance: PlazaAppearance) => {
      if (!clientRef.current?.connected || !channelId) return;
      clientRef.current.publish({
        destination: `/app/plaza/${channelId}/appearance`,
        body: JSON.stringify({ appearance }),
      });
    },
    [channelId],
  );

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return { sendInput, sendChat, sendAppearance };
};
