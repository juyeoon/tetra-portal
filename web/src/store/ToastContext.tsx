import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Alert, Snackbar, type AlertColor } from '@mui/material';
import { shadow } from '@/theme/theme';

type ShowToast = (message: string, severity?: AlertColor) => void;

const ToastContext = createContext<ShowToast>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ key: number; message: string; severity: AlertColor } | null>(null);

  const show = useCallback<ShowToast>((message, severity = 'success') => {
    setToast({ key: Date.now(), message, severity });
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <Snackbar
        key={toast?.key}
        open={!!toast}
        autoHideDuration={2600}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          variant="filled"
          severity={toast?.severity ?? 'success'}
          onClose={() => setToast(null)}
          sx={{ minWidth: 280, boxShadow: shadow.float }}
        >
          {toast?.message}
        </Alert>
      </Snackbar>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
