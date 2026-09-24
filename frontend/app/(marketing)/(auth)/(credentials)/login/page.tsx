"use client";

import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { PasswordInput } from "@/components/password-input";
import { Checkbox } from "@/components/shadcn/checkbox";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

import { useLogin } from "@/hooks/generated/authentication-management/authentication-management";
import { LoginBody } from "@/hooks/zod/authentication-management/authentication-management";
import { safeReturnToPath } from "@/lib/return-to";
import { useRouter, useSearchParams } from "next/navigation";

const LoginFormSchema = LoginBody;

type LoginFormValues = z.infer<typeof LoginFormSchema>;

const AUTH_INPUT_CLASSES = "h-auto px-4 py-[13px] text-sm placeholder:text-icon-muted";

function OAuthErrorToast() {
  const searchParams = useSearchParams();

  useEffect(() => {
    const error = searchParams.get("error");
    if (error) {
      toast.error("Google sign-in failed. Try again or use email and password.");
    }
  }, [searchParams]);

  return null;
}

function ReturnToCapture({ onCapture }: { onCapture: (returnTo: string | null) => void }) {
  const searchParams = useSearchParams();

  useEffect(() => {
    onCapture(safeReturnToPath(searchParams.get("returnTo")));
  }, [searchParams, onCapture]);

  return null;
}

export default function LoginPage() {
  const router = useRouter();
  const [returnTo, setReturnTo] = useState<string | null>(null);

  const { mutate, isPending } = useLogin({
    mutation: {
      onSuccess: () => {
        router.push(returnTo ?? "/playlists");
      },
      onError: () => {
        toast.error("Invalid email or password.");
      },
    },
  });

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(LoginFormSchema),
    defaultValues: {
      email: "",
      password: "",
      rememberMe: false,
    },
  });

  function onSubmit(values: LoginFormValues) {
    mutate({
      data: {
        email: values.email,
        password: values.password,
        rememberMe: values.rememberMe,
      },
    });
  }

  return (
    <>
      <Suspense fallback={null}>
        <OAuthErrorToast />
        <ReturnToCapture onCapture={setReturnTo} />
      </Suspense>

      <div className="w-full mb-[26px] text-center">
        <h1
          className="font-display text-2xl text-marketing-accent"
          style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
        >
          Welcome back
        </h1>
        <p className="text-muted-foreground text-sm mt-2">
          Log in to keep your streak alive.
        </p>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="w-full">
          <div className="space-y-[18px]">
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem className="gap-[7px]">
                  <FormLabel className="text-[13px] font-semibold text-muted-foreground">
                    Email
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      autoComplete="email"
                      placeholder="you@example.com"
                      className={AUTH_INPUT_CLASSES}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem className="gap-[7px]">
                    <FormLabel className="text-[13px] font-semibold text-muted-foreground">
                      Password
                    </FormLabel>
                    <FormControl>
                      <PasswordInput
                        autoComplete="current-password"
                        placeholder="••••••••••"
                        className={AUTH_INPUT_CLASSES}
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

            <div className="flex items-center justify-between gap-2 !mt-4 !mb-7">
            <FormField
              control={form.control}
              name="rememberMe"
              render={({ field }) => (
                <FormItem className="flex items-center gap-[9px]">
                  <FormControl>
                    <Checkbox
                      id="remember-me"
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      className="size-[18px] rounded-[5px] border-2 border-border bg-background accent-primary"
                    />
                  </FormControl>
                  <FormLabel htmlFor="remember-me" className="!mt-0 text-[13px] font-normal text-muted-foreground cursor-pointer">
                    Remember me
                  </FormLabel>
                </FormItem>
              )}
            />
              <Link href="/forgot-password" className="text-[13px] text-primary underline underline-offset-[3px]">
                Forgot password?
              </Link>
            </div>

            <Button type="submit" className="auth-submit w-full" disabled={isPending}>
              {isPending ? "Signing in..." : "Log in"}
            </Button>
          </div>
        </form>
      </Form>

      <div className="flex items-center gap-3 w-full mt-6">
        <div className="flex-1 h-0.5 bg-border" />
        <span className="text-xs text-muted-foreground">or</span>
        <div className="flex-1 h-0.5 bg-border" />
      </div>

        <Button asChild variant="outline" className="auth-google w-full mt-5">
        <a href={`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/oauth2/authorization/google${returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ""}`}>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="18"
            height="18"
            viewBox="0 0 256 262"
          >
            <path
              fill="#4285f4"
              d="M255.878 133.451c0-10.734-.871-18.567-2.756-26.69H130.55v48.448h71.947c-1.45 12.04-9.283 30.172-26.69 42.356l-.244 1.622l38.755 30.023l2.685.268c24.659-22.774 38.875-56.282 38.875-96.027"
            />
            <path
              fill="#34a853"
              d="M130.55 261.1c35.248 0 64.839-11.605 86.453-31.622l-41.196-31.913c-11.024 7.688-25.82 13.055-45.257 13.055c-34.523 0-63.824-22.773-74.269-54.25l-1.531.13l-40.298 31.187l-.527 1.465C35.393 231.798 79.49 261.1 130.55 261.1"
            />
            <path
              fill="#fbbc05"
              d="M56.281 156.37c-2.756-8.123-4.351-16.827-4.351-25.82c0-8.994 1.595-17.697 4.206-25.82l-.073-1.73L15.26 71.312l-1.335.635C5.077 89.644 0 109.517 0 130.55s5.077 40.905 13.925 58.602z"
            />
            <path
              fill="#eb4335"
              d="M130.55 50.479c24.514 0 41.05 10.589 50.479 19.438l36.844-35.974C195.245 12.91 165.798 0 130.55 0C79.49 0 35.393 29.301 13.925 71.947l42.211 32.783c10.59-31.477 39.891-54.251 74.414-54.251"
            />
          </svg>
          <span>Continue with Google</span>
        </a>
        </Button>
    </>
  );
}
