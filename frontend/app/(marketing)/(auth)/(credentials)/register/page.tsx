"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

import { useRegister } from "@/hooks/generated/authentication-management/authentication-management";
import { registerBody } from "@/hooks/zod/authentication-management/authentication-management";
import { useRouter } from "next/navigation";

const RegisterFormSchema = registerBody
  .extend({
    confirmPassword: z.string(),
  })
  .refine((formData) => formData.password === formData.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type RegisterFormValues = z.infer<typeof RegisterFormSchema>;

const AUTH_INPUT_CLASSES = "h-auto px-4 py-[13px] text-sm placeholder:text-icon-muted";

export default function RegisterPage() {
  const router = useRouter();
  const { mutate, isPending } = useRegister({
    mutation: {
      onSuccess: () => {
        setTimeout(() => router.push("/playlists"), 1000);
      },
      onError: () => {
        toast.error("Couldn't create account. Username or email may already be in use.");
      },
    },
  });

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(RegisterFormSchema),
    defaultValues: {
      username: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  function onSubmit(values: RegisterFormValues) {
    mutate({
      data: {
        username: values.username,
        email: values.email,
        password: values.password,
      },
    });
  }

  return (
    <>
      <div className="w-full mb-[26px] text-center">
        <h1
          className="font-display text-2xl text-marketing-accent"
          style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
        >
          Join the game
        </h1>
        <p className="text-muted-foreground text-sm mt-2">
          Create an account and start guessing.
        </p>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="w-full">
          <div className="space-y-[18px]">
            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem className="gap-[7px]">
                  <FormLabel className="text-[13px] font-semibold text-muted-foreground">
                    Name
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="Darius" className={AUTH_INPUT_CLASSES} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

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
                    <Input
                      type="password"
                      placeholder="••••••••••"
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
              name="confirmPassword"
              render={({ field }) => (
                <FormItem className="gap-[7px]">
                  <FormLabel className="text-[13px] font-semibold text-muted-foreground">
                    Confirm password
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="password"
                      placeholder="••••••••••"
                      className={AUTH_INPUT_CLASSES}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Button className="w-full" type="submit" disabled={isPending}>
              {isPending ? "Creating account..." : "Create account"}
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
    </>
  );
}
