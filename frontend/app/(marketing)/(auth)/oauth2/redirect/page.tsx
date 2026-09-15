import { Suspense } from "react";
import OAuth2RedirectHandler from "./RedirectHandler";

export default function Page() {
  return (
    <Suspense fallback={<div>Loading authentication...</div>}>
      <OAuth2RedirectHandler />
    </Suspense>
  );
}
