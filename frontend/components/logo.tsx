export const LogoBars = () => {
  return (
    <div className="flex items-end gap-[3px] h-6 shrink-0">
      <span className="w-[5px] h-[11px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1]" />
      <span className="w-[5px] h-[22px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1]" />
      <span className="w-[5px] h-[15px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1]" />
      <span className="w-[5px] h-[24px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1]" />
    </div>
  );
};

export const LogoIcon = () => {
  return (
    <div className="flex items-center gap-2">
      <LogoBars />
      <span className="font-wordmark font-extrabold text-lg text-foreground">
        hittiguess
      </span>
    </div>
  );
};
