import { useState, useEffect, useRef, useCallback } from 'react';
import { ApiResponse, ChatMessage } from '@/types';

/**
 * 방 채팅 상태(메시지 목록/입력값/자동 스크롤)를 관리한다.
 * WebSocket 전송 함수에는 의존하지 않으며, 수신 핸들러(onChatMessage)만 제공한다.
 * (전송은 호출부에서 sendChat 과 input 을 조합)
 */
export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지 도착 시 맨 아래로 스크롤
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const onChatMessage = useCallback((res: ApiResponse<ChatMessage>) => {
    if (res.success && res.data) {
      setMessages((prev) => [...prev, res.data!]);
    }
  }, []);

  return { messages, input, setInput, bottomRef, onChatMessage };
}
