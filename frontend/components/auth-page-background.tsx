// Every pre-login auth screen (login, register, forgot-password, the OAuth2
// redirect screen) shares the same two decorative shapes at the same
// position and size, per docs/design/source/{Login,Register,ForgotPassword,
// OAuth2Redirect}{Dark,Light}.dc.html. Only the colors differ per screen, so
// position/size stay fixed here and colors come in as Tailwind class props.
type AuthPageBackgroundProps = {
  primaryClassName: string;
  secondaryClassName: string;
};

export function AuthPageBackground({
  primaryClassName,
  secondaryClassName,
}: AuthPageBackgroundProps) {
  return (
    <>
      <div
        className={`absolute -top-[180px] -right-40 size-[560px] rounded-full pointer-events-none ${primaryClassName}`}
      />
      <div
        className={`absolute -bottom-[220px] -left-40 size-[480px] rotate-[18deg] pointer-events-none ${secondaryClassName}`}
      />
    </>
  );
}
