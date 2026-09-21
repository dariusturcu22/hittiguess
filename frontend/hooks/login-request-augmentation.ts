// The generated client predates the backend rememberMe field on LoginRequest.
// This merges the landed shape in until the next api:gen run generates it.
declare module "@/hooks/models/loginRequest" {
  interface LoginRequest {
    rememberMe?: boolean;
  }
}

export {};
