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

  // 새 메시지 도착 시 채팅 목록만 맨 아래로 스크롤.
  // scrollIntoView 를 쓰면 채팅창이 화면 밖일 때 페이지 전체가 따라 내려간다
  // (모바일에서 오목판 아래에 채팅이 오므로, 입장 알림 한 줄에 채팅창으로 튕겨 내려갔다).
  useEffect(() => {
    const box = bottomRef.current?.parentElement; // .chatMessages (overflow-y: auto)
    box?.scrollTo({ top: box.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const onChatMessage = useCallback((res: ApiResponse<ChatMessage>) => {
    if (res.success && res.data) {
      setMessages((prev) => [...prev, res.data!]);
    }
  }, []);

  return { messages, input, setInput, bottomRef, onChatMessage };
}
