import { useEffect, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tokenStorage } from '@/utils/token';

interface UseWebSocketOptions {
  gameId: number;
  onMove: (data: unknown) => void;
  onGameStatus: (data: unknown) => void;
}

export const useWebSocket = ({ gameId, onMove, onGameStatus }: UseWebSocketOptions) => {
  const clientRef = useRef<Client | null>(null);
  const isConnectedRef = useRef(false);

  const connect = useCallback(() => {
    const token = tokenStorage.getAccessToken();

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        isConnectedRef.current = true;

        client.subscribe(`/topic/game/${gameId}`, (message: IMessage) => {
          onMove(JSON.parse(message.body));
        });

        client.subscribe(`/topic/game/${gameId}/status`, (message: IMessage) => {
          onGameStatus(JSON.parse(message.body));
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
  }, [gameId, onMove, onGameStatus]);

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    isConnectedRef.current = false;
  }, []);

  const sendMove = useCallback(
    (row: number, col: number) => {
      if (!clientRef.current?.connected) return;
      clientRef.current.publish({
        destination: `/app/game/${gameId}/move`,
        body: JSON.stringify({ row, col }),
      });
    },
    [gameId],
  );

  const sendSurrender = useCallback(() => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: `/app/game/${gameId}/surrender`,
      body: '{}',
    });
  }, [gameId]);

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  return { sendMove, sendSurrender, isConnected: isConnectedRef };
};
