import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import { tokenStorage } from '@/utils/token';
import {
  ApiResponse,
  ChatMessage,
  GameMove,
  NoticePayload,
  PhysicalDirection,
  PhysicalInputType,
  PhysicalSnapshot,
  Room,
} from '@/types';

interface UseWebSocketOptions {
  roomCode: string;
  onMove: (msg: ApiResponse<GameMove>) => void;
  onRoomStatus: (msg: ApiResponse<Room>) => void;
  onRoomClosed?: (msg: NoticePayload) => void;
  onChat?: (msg: ApiResponse<ChatMessage>) => void;
  onNotice?: (msg: NoticePayload) => void;
  // 피지컬 오목: 서버 스냅샷(raw, ApiResponse 래핑 없음). 클래식에서는 미사용.
  onPhysicalSnapshot?: (snapshot: PhysicalSnapshot) => void;
}

const parse = <T>(message: IMessage): T => JSON.parse(message.body) as T;

export const useWebSocket = ({
  roomCode,
  onMove,
  onRoomStatus,
  onRoomClosed,
  onChat,
  onNotice,
  onPhysicalSnapshot,
}: UseWebSocketOptions) => {
  const clientRef = useRef<Client | null>(null);
  const isConnectedRef = useRef(false);

  const connect = useCallback(() => {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 3000,
      // 매 (재)연결 직전마다 localStorage의 최신 토큰을 헤더에 주입.
      // axios 인터셉터가 토큰을 갱신해 두므로, 재연결 시 만료된 토큰 사용을 방지한다.
      beforeConnect: () => {
        client.connectHeaders = {
          Authorization: `Bearer ${tokenStorage.getAccessToken() ?? ''}`,
        };
      },
      onConnect: () => {
        isConnectedRef.current = true;

        client.subscribe(`/topic/room/${roomCode}`, (message: IMessage) => {
          onMove(parse<ApiResponse<GameMove>>(message));
        });

        client.subscribe(`/topic/room/${roomCode}/status`, (message: IMessage) => {
          onRoomStatus(parse<ApiResponse<Room>>(message));
        });

        client.subscribe(`/topic/room/${roomCode}/closed`, (message: IMessage) => {
          onRoomClosed?.(parse<NoticePayload>(message));
        });

        client.subscribe(`/topic/room/${roomCode}/chat`, (message: IMessage) => {
          onChat?.(parse<ApiResponse<ChatMessage>>(message));
        });

        client.subscribe(`/topic/room/${roomCode}/notice`, (message: IMessage) => {
          onNotice?.(parse<NoticePayload>(message));
        });

        client.subscribe(`/topic/room/${roomCode}/physical`, (message: IMessage) => {
          onPhysicalSnapshot?.(parse<PhysicalSnapshot>(message));
        });
      },
      onDisconnect: () => {
        isConnectedRef.current = false;
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [roomCode, onMove, onRoomStatus, onRoomClosed, onChat, onNotice, onPhysicalSnapshot]);

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    isConnectedRef.current = false;
  }, []);

  const sendMove = useCallback(
    (row: number, col: number) => {
      if (!clientRef.current?.connected) return;
      clientRef.current.publish({
        destination: `/app/game/${roomCode}/move`,
        body: JSON.stringify({ row, col }),
      });
    },
    [roomCode],
  );

  const sendSurrender = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/game/${roomCode}/surrender`,
      body: '{}',
    });
  }, [roomCode]);

  const sendReady = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/game/${roomCode}/ready`,
      body: '{}',
    });
  }, [roomCode]);

  const sendStart = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/game/${roomCode}/start`,
      body: '{}',
    });
  }, [roomCode]);

  const sendChat = useCallback(
    (content: string) => {
      if (!clientRef.current?.connected) return;
      clientRef.current.publish({
        destination: `/app/game/${roomCode}/chat`,
        body: JSON.stringify({ content }),
      });
    },
    [roomCode],
  );

  // 대기 중 바둑알 스킨 변경 — 서버가 장착 후 방 상태를 양쪽에 브로드캐스트한다.
  // itemId=null 이면 기본 스킨으로 되돌림(장착 해제).
  const sendChangeSkin = useCallback(
    (itemId: number | null) => {
      if (!clientRef.current?.connected) return;
      clientRef.current.publish({
        destination: `/app/game/${roomCode}/change-skin`,
        body: JSON.stringify({ itemId }),
      });
    },
    [roomCode],
  );

  // 피지컬 오목: 단일 입력 엔드포인트로 전송(direction 은 MOVE_START 일 때만 의미)
  const sendPhysicalInput = useCallback(
    (type: PhysicalInputType, direction?: PhysicalDirection) => {
      if (!clientRef.current?.connected) return;
      clientRef.current.publish({
        destination: `/app/physical/${roomCode}/input`,
        body: JSON.stringify({ type, direction: direction ?? null }),
      });
    },
    [roomCode],
  );

  const sendPhysicalSurrender = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/physical/${roomCode}/surrender`,
      body: '{}',
    });
  }, [roomCode]);

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return {
    sendMove,
    sendSurrender,
    sendReady,
    sendStart,
    sendChat,
    sendChangeSkin,
    sendPhysicalInput,
    sendPhysicalSurrender,
    isConnected: isConnectedRef,
  };
};
