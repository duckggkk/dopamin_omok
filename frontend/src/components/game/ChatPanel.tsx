import React from 'react';
import { ChatMessage } from '@/types';
import styles from '@/pages/GamePage.module.css';

interface ChatPanelProps {
  messages: ChatMessage[];
  input: string;
  setInput: (value: string) => void;
  onSend: () => void;
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  myNickname?: string;
  bottomRef: React.RefObject<HTMLDivElement>;
}

const senderColor = (color: ChatMessage['senderColor']): string =>
  color === 'BLACK' ? 'var(--text-muted)' : color === 'WHITE' ? 'var(--gold-bright)' : 'var(--info)';

const ChatPanel = ({ messages, input, setInput, onSend, onKeyDown, myNickname, bottomRef }: ChatPanelProps) => (
  <div className={styles.chatPanel}>
    <h3 className={styles.panelTitle}>채팅</h3>
    <div className={styles.chatMessages}>
      {messages.map((msg, i) => {
        const isMyMsg = msg.senderNickname === myNickname;
        return (
          <div key={i} className={`${styles.chatMessage} ${isMyMsg ? styles.chatMine : styles.chatOther}`}>
            <span className={styles.chatSender} style={{ color: senderColor(msg.senderColor) }}>
              {msg.spectator ? '👁 ' : ''}{msg.senderNickname}
            </span>
            <span className={styles.chatContent}>{msg.content}</span>
          </div>
        );
      })}
      <div ref={bottomRef} />
    </div>
    <div className={styles.chatInputRow}>
      <input
        className={styles.chatInput}
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder="메시지 입력..."
        maxLength={200}
      />
      <button className={styles.chatSendBtn} onClick={onSend}>
        전송
      </button>
    </div>
  </div>
);

export default ChatPanel;
