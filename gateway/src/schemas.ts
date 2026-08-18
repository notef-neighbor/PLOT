import { z } from "zod";

export const EventInput = z.object({
  at: z.iso.datetime({ offset: true }),
  application: z.string().trim().min(1).max(200),
  kind: z.string().trim().min(1).max(80),
  text: z.string().max(8_000).nullable().optional(),
  contentRedacted: z.boolean().default(false),
});

export const SummarizeInput = z.object({
  sessionId: z.string().trim().min(1).max(200),
  locale: z.string().trim().min(2).max(35),
  events: z.array(EventInput).min(1).max(500),
});

export const MemoryInput = z.object({
  id: z.string().trim().min(1).max(200),
  startedAt: z.iso.datetime({ offset: true }),
  endedAt: z.iso.datetime({ offset: true }),
  title: z.string().max(200),
  summary: z.string().max(4_000),
  applications: z.array(z.string().max(200)).max(30),
});

export const AskInput = z.object({
  question: z.string().trim().min(1).max(1_000),
  memories: z.array(MemoryInput).max(30),
});

export const SummaryOutput = z.object({
  title: z.string().min(1).max(120),
  summary: z.string().min(1).max(2_000),
  keywords: z.array(z.string().min(1).max(80)).max(20),
  status: z.enum(["completed", "in_progress", "unknown"]),
});

export const AnswerOutput = z.object({
  answer: z.string().min(1).max(8_000),
});

export type SummarizeRequest = z.infer<typeof SummarizeInput>;
export type AskRequest = z.infer<typeof AskInput>;
export type SummaryResponse = z.infer<typeof SummaryOutput>;
export type AnswerResponse = z.infer<typeof AnswerOutput>;
