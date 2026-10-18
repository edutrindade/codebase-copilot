import { Toast as RadixToast } from 'radix-ui';
import { CircleAlert, CircleCheck, X } from 'lucide-react';
import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { messages } from '../../messages';
import './Toast.css';

export interface ToastMessage {
  tone: 'success' | 'danger';
  title: string;
  description?: string;
}

type Notify = (toast: ToastMessage) => void;

// Without a provider (isolated screens in tests) notifications are dropped.
const ToastContext = createContext<Notify>(() => undefined);

export function useToast(): Notify {
  return useContext(ToastContext);
}

// Confirmations only (P6): errors that need action stay where they happened.
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<(ToastMessage & { id: number })[]>([]);
  const nextId = useRef(0);
  const notify = useCallback<Notify>((toast) => {
    const id = nextId.current++;
    setToasts((list) => [...list, { ...toast, id }]);
  }, []);

  return (
    <ToastContext.Provider value={notify}>
      <RadixToast.Provider label={messages.toast.region} swipeDirection="right">
        {children}
        {toasts.map((toast) => {
          const Icon = toast.tone === 'success' ? CircleCheck : CircleAlert;
          return (
            <RadixToast.Root
              key={toast.id}
              type="background"
              className={`toast toast--${toast.tone}`}
              onOpenChange={(open) => {
                if (!open) {
                  setToasts((list) => list.filter((item) => item.id !== toast.id));
                }
              }}
            >
              <Icon aria-hidden="true" className="toast__icon" />
              <div className="toast__body">
                <RadixToast.Title className="toast__title">{toast.title}</RadixToast.Title>
                {toast.description && (
                  <RadixToast.Description className="toast__description">
                    {toast.description}
                  </RadixToast.Description>
                )}
              </div>
              <RadixToast.Close className="toast__close" aria-label={messages.toast.close}>
                <X aria-hidden="true" />
              </RadixToast.Close>
            </RadixToast.Root>
          );
        })}
        <RadixToast.Viewport className="toast-viewport" />
      </RadixToast.Provider>
    </ToastContext.Provider>
  );
}
