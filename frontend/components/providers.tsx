"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import axios from "axios";
import { useState } from "react";
import { ThemeProvider } from "@/components/theme-provider";
import { Toaster } from "@/components/shadcn/sonner";

export default function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (failureCount, error) => {
              if (axios.isAxiosError(error) && error.response?.status === 401) {
                return false;
              }
              return failureCount < 3;
            },
            staleTime: 60 * 1000,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider
        attribute="class"
        defaultTheme="system"
        enableSystem
        disableTransitionOnChange
      >
        {children}
        <Toaster />
      </ThemeProvider>
    </QueryClientProvider>
  );
}
