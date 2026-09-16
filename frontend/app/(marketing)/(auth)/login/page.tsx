"use client";

import { Suspense, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Card } from "@/components/shadcn/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

import { useLogin } from "@/hooks/generated/authentication-management/authentication-management";
import { loginBody } from "@/hooks/zod/authentication-management/authentication-management";
import { useRouter, useSearchParams } from "next/navigation";

const LoginFormSchema = loginBody;

type LoginFormValues = z.infer<typeof LoginFormSchema>;

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

export default function LoginPage() {
  const router = useRouter();

  const { mutate, isPending } = useLogin({
    mutation: {
      onSuccess: () => {
        setTimeout(() => router.push("/playlists"), 1000);
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
    },
  });

  function onSubmit(values: LoginFormValues) {
    mutate({
      data: {
        email: values.email,
        password: values.password,
      },
    });
  }

  return (
    <section className="flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <div className="absolute -top-44 -right-40 size-[560px] rounded-full bg-accent/15 pointer-events-none" />
      <div className="absolute -bottom-56 -left-40 size-[480px] bg-primary/10 rotate-12 pointer-events-none" />

      <Suspense fallback={null}>
        <OAuthErrorToast />
      </Suspense>

      <Card className="w-full max-w-[460px] px-8 py-11 relative z-10">
        <div className="flex items-center gap-1 bg-muted rounded-full p-1 w-full">
          <Button asChild size="sm" className="flex-1">
            <Link href="/login">Log in</Link>
          </Button>
          <Button asChild variant="ghost" size="sm" className="flex-1">
            <Link href="/register">Create account</Link>
          </Button>
        </div>

        <div className="text-center mt-2">
          <h1
            className="font-display text-2xl text-accent"
            style={{ textShadow: "3px 3px 0 var(--background)" }}
          >
            Welcome back
          </h1>
          <p className="text-muted-foreground text-sm mt-2">
            Log in to keep your streak alive.
          </p>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="w-full">
            <div className="space-y-4 mt-2">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="you@example.com"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div>
                <div className="flex items-center justify-between mb-2">
                  <FormLabel>Password</FormLabel>
                  <Button
                    asChild
                    variant="link"
                    className="h-auto p-0 text-xs text-muted-foreground"
                  >
                    <Link href="/forgot-password">Forgot password?</Link>
                  </Button>
                </div>
                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input
                          type="password"
                          placeholder="••••••••••"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <Button type="submit" className="w-full mt-2" disabled={isPending}>
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

        <a
          href={`${process.env.NEXT_PUBLIC_API_URL}/oauth2/authorization/google`}
          className="w-full mt-5 block"
        >
          <Button type="button" variant="outline" className="w-full">
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
          </Button>
        </a>
      </Card>
    </section>
  );
}
