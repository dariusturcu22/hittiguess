import axios, { AxiosRequestConfig, AxiosResponse } from "axios";

export const AXIOS_INSTANCE = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  withCredentials: true,
});

AXIOS_INSTANCE.interceptors.request.use((config) => {
  const csrfToken = document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];

  if (csrfToken) {
    config.headers["X-XSRF-TOKEN"] = csrfToken;
  }
  return config;
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: unknown) => {
  failedQueue.forEach((p) => (error ? p.reject(error) : p.resolve(null)));
  failedQueue = [];
};

AXIOS_INSTANCE.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const url = originalRequest?.url ?? "";
    const isAuthEndpoint = url.includes("/auth/");

    if (
      error.response?.status === 401 &&
      !isAuthEndpoint &&
      !originalRequest._retry
    ) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(() => AXIOS_INSTANCE(originalRequest));
      }
      originalRequest._retry = true;
      isRefreshing = true;

      try {
        await axios.post(
          `${AXIOS_INSTANCE.defaults.baseURL}/auth/refresh`,
          {},
          { withCredentials: true },
        );

        processQueue(null);
        return AXIOS_INSTANCE(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        // Outside React here, no router available; a hard redirect also
        // clears all in-memory app state on session expiry.
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.href = "/login";
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
);

export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  return AXIOS_INSTANCE({
    ...config,
    ...options,
  }).then((response: AxiosResponse<T>) => response.data);
};
