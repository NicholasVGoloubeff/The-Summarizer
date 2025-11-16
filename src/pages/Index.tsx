import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card } from "@/components/ui/card";
import { Loader2, Sparkles, FileText, AlertCircle } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

const Index = () => {
  const [transcript, setTranscript] = useState("");
  const [questions, setQuestions] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const { toast } = useToast();

  const handleGenerateQuestions = async () => {
    if (!transcript.trim()) {
      toast({
        title: "Transcript Required",
        description: "Please paste a lecture transcript before generating questions.",
        variant: "destructive",
      });
      return;
    }

    setIsLoading(true);
    setQuestions([]);

    try {
      const response = await fetch("http://localhost:8080/api/answers", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ prompt: transcript }),
      });

      if (!response.ok) {
        throw new Error(`Server error: ${response.status}`);
      }

      const data = await response.json();
      
      if (data.answers && Array.isArray(data.answers)) {
        setQuestions(data.answers);
        toast({
          title: "Questions Generated!",
          description: `Successfully generated ${data.answers.length} questions.`,
        });
      } else {
        throw new Error("Invalid response format");
      }
    } catch (error) {
      console.error("Error generating questions:", error);
      toast({
        title: "Generation Failed",
        description: error instanceof Error ? error.message : "Failed to generate questions. Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-background via-background to-secondary/30">
      {/* Header */}
      <header className="border-b border-border/50 backdrop-blur-sm bg-background/80 sticky top-0 z-10">
        <div className="container mx-auto px-4 py-6">
          <div className="flex items-center justify-center gap-3">
            <div className="relative">
              <Sparkles className="w-8 h-8 text-primary animate-pulse-glow" />
            </div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-primary via-accent to-primary-light bg-clip-text text-transparent animate-fade-in">
              LectureNav
            </h1>
          </div>
          <p className="text-center text-muted-foreground mt-2 animate-fade-in">
            AI-Powered Lecture Question Generator
          </p>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-12 max-w-5xl">
        <div className="space-y-8 animate-fade-in-up">
          {/* Input Section */}
          <Card className="p-6 md:p-8 shadow-elevated border-border/50 bg-card/50 backdrop-blur-sm">
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-primary" />
                <label htmlFor="transcript" className="text-lg font-semibold text-foreground">
                  Lecture Transcript
                </label>
              </div>
              <Textarea
                id="transcript"
                placeholder="Paste your lecture transcript here... The AI will analyze it and generate insightful questions to help you study."
                value={transcript}
                onChange={(e) => setTranscript(e.target.value)}
                className="min-h-[200px] text-base resize-none border-border/60 focus:border-primary focus:ring-primary/20 transition-all"
                disabled={isLoading}
              />
              <div className="flex items-center justify-between pt-2">
                <p className="text-sm text-muted-foreground">
                  {transcript.length} characters
                </p>
                <Button
                  onClick={handleGenerateQuestions}
                  disabled={isLoading || !transcript.trim()}
                  size="lg"
                  className="bg-gradient-to-r from-primary to-accent hover:from-primary-dark hover:to-accent text-primary-foreground font-semibold px-8 shadow-lg hover:shadow-xl transition-all disabled:opacity-50"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                      Generating...
                    </>
                  ) : (
                    <>
                      <Sparkles className="mr-2 h-5 w-5" />
                      Generate Questions
                    </>
                  )}
                </Button>
              </div>
            </div>
          </Card>

          {/* Loading State */}
          {isLoading && (
            <div className="flex flex-col items-center justify-center py-12 animate-fade-in">
              <Loader2 className="w-12 h-12 text-primary animate-spin mb-4" />
              <p className="text-lg text-muted-foreground">
                Analyzing transcript and generating questions...
              </p>
            </div>
          )}

          {/* Results Section */}
          {!isLoading && questions.length > 0 && (
            <div className="space-y-6 animate-fade-in-up">
              <div className="flex items-center gap-2">
                <Sparkles className="w-6 h-6 text-accent" />
                <h2 className="text-2xl font-bold text-foreground">
                  Generated Questions
                </h2>
                <span className="ml-auto text-sm font-medium text-muted-foreground bg-secondary px-3 py-1 rounded-full">
                  {questions.length} {questions.length === 1 ? 'question' : 'questions'}
                </span>
              </div>

              <div className="grid gap-4">
                {questions.map((question, index) => (
                  <Card
                    key={index}
                    className="p-6 hover:shadow-elevated transition-all duration-300 border-border/50 bg-card/80 backdrop-blur-sm hover:border-primary/30 group animate-slide-in"
                    style={{ animationDelay: `${index * 0.1}s` }}
                  >
                    <div className="flex gap-4">
                      <div className="flex-shrink-0">
                        <div className="w-10 h-10 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-primary-foreground font-bold shadow-md group-hover:scale-110 transition-transform">
                          {index + 1}
                        </div>
                      </div>
                      <div className="flex-1">
                        <p className="text-base leading-relaxed text-card-foreground">
                          {question}
                        </p>
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            </div>
          )}

          {/* Empty State */}
          {!isLoading && questions.length === 0 && transcript && (
            <Card className="p-12 text-center border-dashed border-2 border-border/50 bg-muted/20 animate-fade-in">
              <AlertCircle className="w-12 h-12 text-muted-foreground mx-auto mb-4" />
              <p className="text-lg text-muted-foreground">
                Click "Generate Questions" to analyze your transcript
              </p>
            </Card>
          )}
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-border/50 mt-16 py-8 bg-background/50 backdrop-blur-sm">
        <div className="container mx-auto px-4 text-center text-sm text-muted-foreground">
          <p>Powered by AI • Built for Learning</p>
        </div>
      </footer>
    </div>
  );
};

export default Index;
