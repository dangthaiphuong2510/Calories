-- exercise_entries, water_entries, profiles (offline-first sync)
-- Apply in Supabase SQL editor or via CLI: supabase db push

create extension if not exists "pgcrypto";

-- ─── exercise_entries ───────────────────────────────────────────────────────
create table if not exists public.exercise_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  calories_burned double precision not null default 0,
  duration_minutes integer not null default 0,
  created_at timestamptz not null default now()
);

create index if not exists idx_exercise_entries_user_created
  on public.exercise_entries (user_id, created_at desc);

alter table public.exercise_entries enable row level security;

create policy "exercise_entries_select_own"
  on public.exercise_entries for select
  using (auth.uid() = user_id);

create policy "exercise_entries_insert_own"
  on public.exercise_entries for insert
  with check (auth.uid() = user_id);

create policy "exercise_entries_update_own"
  on public.exercise_entries for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "exercise_entries_delete_own"
  on public.exercise_entries for delete
  using (auth.uid() = user_id);

-- ─── water_entries ──────────────────────────────────────────────────────────
create table if not exists public.water_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  amount_ml integer not null check (amount_ml > 0),
  created_at timestamptz not null default now()
);

create index if not exists idx_water_entries_user_created
  on public.water_entries (user_id, created_at desc);

alter table public.water_entries enable row level security;

create policy "water_entries_select_own"
  on public.water_entries for select
  using (auth.uid() = user_id);

create policy "water_entries_insert_own"
  on public.water_entries for insert
  with check (auth.uid() = user_id);

create policy "water_entries_update_own"
  on public.water_entries for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "water_entries_delete_own"
  on public.water_entries for delete
  using (auth.uid() = user_id);

-- ─── profiles ───────────────────────────────────────────────────────────────
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  avatar_url text,
  gender text,
  birth_date text
);

alter table public.profiles enable row level security;

create policy "profiles_select_own"
  on public.profiles for select
  using (auth.uid() = id);

create policy "profiles_insert_own"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "profiles_update_own"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

create policy "profiles_delete_own"
  on public.profiles for delete
  using (auth.uid() = id);
