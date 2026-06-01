import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import { tokenStorage } from '@/utils/token';

interface UseWebSocketOptions {
  roomCode: string;
  onMove: (data: unknown) => void;
  onRoomStatus: (data: unknown) => void;
  onRoomClosed?: (data: unknown) => void;
  onChat?: (data: unknown) => void;
  onNotice?: (data: unknown) => void;
}

export const useWebSocket = ({
  roomCode,
  onMove,
  onRoomStatus,
  onRoomClosed,
  onChat,
  onNotice,
}: UseWebSocketOptions) => {
  const clientRef = useRef<Client | null>(null);
  const isConnectedRef = useRef(false);

  const connect = useCallback(() => {
    const token = tokenStorage.getAccessToken();

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        isConnectedRef.current = true;

        client.subscribe(`/topic/room/${roomCode}`, (message: IMessage) => {
          onMove(JSON.parse(message.body));
        });

        client.subscribe(`/topic/room/${roomCode}/status`, (message: IMessage) => {
          onRoomStatus(JSON.parse(message.body));
        });

        client.subscribe(`/topic/room/${roomCode}/closed`, (message: IMessage) => {
          onRoomClosed?.(JSON.parse(message.body));
        });

        client.subscribe(`/topic/room/${roomCode}/chat`, (message: IMessage) => {
          onChat?.(JSON.parse(message.body));
        });

        client.subscribe(`/topic/room/${roomCode}/notice`, (message: IMessage) => {
          onNotice?.(JSON.parse(message.body));
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
  }, [roomCode, onMove, onRoomStatus, onRoomClosed, onChat, onNotice]);

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

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return { sendMove, sendSurrender, sendReady, sendStart, sendChat, isConnected: isConnectedRef };
};
