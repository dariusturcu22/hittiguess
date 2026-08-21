"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
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

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const error = searchParams.get("error");
    if (error) {
      toast.error("Google sign-in failed. Try again or use email and password.");
    }
  }, [searchParams]);

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
    <section className="flex px-4 py-8">
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit)}
          className="max-w-92 m-auto h-fit w-full"
        >
          <div className="p-6">
            <div>
              <h1 className="mb-1 mt-4 text-xl font-semibold">Welcome back</h1>
              <p className="text-muted-foreground text-sm">
                Sign in to your account to continue
              </p>
            </div>

            <div className="mt-6">
              <a
                href={`${process.env.NEXT_PUBLIC_API_URL}/oauth2/authorization/google`}
                className="flex items-center gap-2"
              >
                <Button type="button" variant="outline" className="w-full">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="0.98em"
                    height="1em"
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
            </div>

            <div className="my-6 grid grid-cols-[1fr_auto_1fr] items-center gap-3">
              <hr className="border-dashed" />
              <span className="text-muted-foreground text-xs">
                Or continue with
              </span>
              <hr className="border-dashed" />
            </div>

            <div className="space-y-5">
              <div className="space-y-2">
                {/* Email */}
                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="text-sm">Email</FormLabel>
                      <FormControl>
                        <Input
                          type="email"
                          placeholder="johndoe or johndoe@email.com"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              {/* Password */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <FormLabel className="text-sm">Password</FormLabel>
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
                          placeholder="••••••••"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <Button type="submit" className="w-full" disabled={isPending}>
                {isPending ? "Signing in..." : "Sign In"}
              </Button>
            </div>
          </div>

          <p className="text-muted-foreground text-center text-sm">
            Don't have an account?
            <Button asChild variant="link" className="px-2">
              <Link href="/register">Create one</Link>
            </Button>
          </p>
        </form>
      </Form>
    </section>
  );
}
