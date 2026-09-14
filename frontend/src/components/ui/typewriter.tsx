"use client";

import { motion, type Variants, useReducedMotion } from "motion/react";
import {
  type ElementType,
  type HTMLAttributes,
  type ReactNode,
  useEffect,
  useMemo,
  useState,
} from "react";
import { cn } from "@/lib/utils";

export type TypewriterProps = {
  text: string | string[];
  as?: ElementType;
  speed?: number;
  initialDelay?: number;
  waitTime?: number;
  deleteSpeed?: number;
  loop?: boolean;
  className?: string;
  showCursor?: boolean;
  hideCursorOnType?: boolean;
  cursorChar?: string | ReactNode;
  cursorClassName?: string;
  cursorAnimationVariants?: {
    initial: Variants["initial"];
    animate: Variants["animate"];
  };
} & HTMLAttributes<HTMLElement>;

export function Typewriter({
  text,
  as: Tag = "div",
  speed = 50,
  initialDelay = 0,
  waitTime = 2000,
  deleteSpeed = 30,
  loop = true,
  className,
  showCursor = true,
  hideCursorOnType = false,
  cursorChar = "|",
  cursorClassName = "ml-1",
  cursorAnimationVariants = {
    animate: {
      opacity: 1,
      transition: {
        duration: 0.01,
        repeat: Number.POSITIVE_INFINITY,
        repeatDelay: 0.4,
        repeatType: "reverse",
      },
    },
    initial: { opacity: 0 },
  },
  ...props
}: TypewriterProps) {
  const reduceMotion = useReducedMotion();
  const [displayText, setDisplayText] = useState("");
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);
  const [currentTextIndex, setCurrentTextIndex] = useState(0);

  const texts = useMemo(() => (Array.isArray(text) ? text : [text]), [text]);

  useEffect(() => {
    if (reduceMotion) return;

    let timeout: ReturnType<typeof setTimeout>;
    const currentText = texts[currentTextIndex] ?? "";

    const tick = () => {
      if (isDeleting) {
        if (displayText === "") {
          setIsDeleting(false);
          if (currentTextIndex === texts.length - 1 && !loop) return;
          setCurrentTextIndex((previous) => (previous + 1) % texts.length);
          setCurrentIndex(0);
        } else {
          timeout = setTimeout(
            () => setDisplayText((previous) => previous.slice(0, -1)),
            deleteSpeed,
          );
        }
      } else if (currentIndex < currentText.length) {
        timeout = setTimeout(() => {
          setDisplayText((previous) => previous + currentText[currentIndex]);
          setCurrentIndex((previous) => previous + 1);
        }, speed);
      } else if (texts.length > 1) {
        timeout = setTimeout(() => setIsDeleting(true), waitTime);
      }
    };

    if (currentIndex === 0 && !isDeleting && displayText === "") {
      timeout = setTimeout(tick, initialDelay);
    } else {
      tick();
    }

    return () => clearTimeout(timeout);
  }, [
    currentIndex,
    displayText,
    isDeleting,
    speed,
    deleteSpeed,
    waitTime,
    texts,
    currentTextIndex,
    loop,
    initialDelay,
    reduceMotion,
  ]);

  const isTyping =
    !reduceMotion &&
    (currentIndex < (texts[currentTextIndex]?.length ?? 0) || isDeleting);
  const visibleText = reduceMotion ? (texts[0] ?? "") : displayText;

  return (
    <Tag
      className={cn("inline whitespace-pre-wrap tracking-tight", className)}
      {...props}
    >
      <span>{visibleText}</span>
      {showCursor && (
        <motion.span
          animate={reduceMotion ? undefined : "animate"}
          className={cn(
            cursorClassName,
            hideCursorOnType && isTyping ? "hidden" : "",
          )}
          initial={reduceMotion ? false : "initial"}
          variants={cursorAnimationVariants as Variants}
        >
          {cursorChar}
        </motion.span>
      )}
    </Tag>
  );
}

export default Typewriter;
