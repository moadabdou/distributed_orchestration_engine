export type WorkerTheme = {
  bg: string;
  text: string;
  dot: string;
  icon: string;
  glow: string;
  stroke: string;
};

const THEMES: WorkerTheme[] = [
  { bg: 'bg-emerald-100', text: 'text-emerald-700', dot: 'bg-emerald-400', icon: 'text-emerald-500', glow: 'shadow-[inset_0_0_20px_rgba(52,211,153,0.2)]', stroke: '#34d399' },
  { bg: 'bg-blue-100', text: 'text-blue-700', dot: 'bg-blue-400', icon: 'text-blue-500', glow: 'shadow-[inset_0_0_20px_rgba(96,165,250,0.2)]', stroke: '#60a5fa' },
  { bg: 'bg-indigo-100', text: 'text-indigo-700', dot: 'bg-indigo-400', icon: 'text-indigo-500', glow: 'shadow-[inset_0_0_20px_rgba(129,140,248,0.2)]', stroke: '#818cf8' },
  { bg: 'bg-purple-100', text: 'text-purple-700', dot: 'bg-purple-400', icon: 'text-purple-500', glow: 'shadow-[inset_0_0_20px_rgba(192,132,252,0.2)]', stroke: '#c084fc' },
  { bg: 'bg-fuchsia-100', text: 'text-fuchsia-700', dot: 'bg-fuchsia-400', icon: 'text-fuchsia-500', glow: 'shadow-[inset_0_0_20px_rgba(232,121,249,0.2)]', stroke: '#e879f9' },
  { bg: 'bg-pink-100', text: 'text-pink-700', dot: 'bg-pink-400', icon: 'text-pink-500', glow: 'shadow-[inset_0_0_20px_rgba(244,114,182,0.2)]', stroke: '#f472b6' },
  { bg: 'bg-rose-100', text: 'text-rose-700', dot: 'bg-rose-400', icon: 'text-rose-500', glow: 'shadow-[inset_0_0_20px_rgba(251,113,133,0.2)]', stroke: '#fb7185' },
  { bg: 'bg-orange-100', text: 'text-orange-700', dot: 'bg-orange-400', icon: 'text-orange-500', glow: 'shadow-[inset_0_0_20px_rgba(251,146,60,0.2)]', stroke: '#fb923c' },
  { bg: 'bg-amber-100', text: 'text-amber-700', dot: 'bg-amber-400', icon: 'text-amber-500', glow: 'shadow-[inset_0_0_20px_rgba(251,191,36,0.2)]', stroke: '#fbbf24' },
  { bg: 'bg-teal-100', text: 'text-teal-700', dot: 'bg-teal-400', icon: 'text-teal-500', glow: 'shadow-[inset_0_0_20px_rgba(45,212,191,0.2)]', stroke: '#2dd4bf' },
];

const OFFLINE_THEME: WorkerTheme = {
  bg: 'bg-slate-200 dark:bg-slate-700',
  text: 'text-slate-500 dark:text-slate-400',
  dot: 'bg-slate-400 dark:bg-slate-500',
  icon: 'text-slate-400 dark:text-slate-500',
  glow: 'shadow-none',
  stroke: '#94a3b8' // text-slate-400 color
};

export const getWorkerTheme = (idOrHostname: string, isOffline: boolean = false): WorkerTheme => {
  if (isOffline) {
    return OFFLINE_THEME;
  }
  
  if (!idOrHostname) {
    return THEMES[0];
  }
  
  let hash = 0;
  for (let i = 0; i < idOrHostname.length; i++) {
    hash = idOrHostname.charCodeAt(i) + ((hash << 5) - hash);
  }
  
  const index = Math.abs(hash) % THEMES.length;
  return THEMES[index];
};
