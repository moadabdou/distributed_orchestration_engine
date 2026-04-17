import React, { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { createJob } from '../api/jobs';
import { X, Play } from 'lucide-react';

interface SubmitJobModalProps {
  onClose: () => void;
}

const SubmitJobModal: React.FC<SubmitJobModalProps> = ({ onClose }) => {
  const queryClient = useQueryClient();
  const [payloadText, setPayloadText] = useState('{\n  "type": "Data Ingest",\n  "target": "example"\n}');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError('');

    let parsed;
    try {
      parsed = JSON.parse(payloadText);
    } catch {
      setError('Invalid JSON payload');
      setIsSubmitting(false);
      return;
    }

    try {
      await createJob({ payload: JSON.stringify(parsed) });
      queryClient.invalidateQueries({ queryKey: ['jobsList'] });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to submit job');
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/60 backdrop-blur-md transition-all duration-300">
      <div className="glass-panel w-full max-w-md p-8 relative animate-in fade-in zoom-in duration-300 border border-white/20 dark:border-white/10 shadow-2xl">
        <button 
          onClick={onClose}
          className="absolute top-5 right-5 w-9 h-9 flex items-center justify-center rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-white dark:hover:bg-slate-700 transition-all border border-transparent hover:border-white/20"
        >
          <X className="w-5 h-5" />
        </button>
        
        <div className="flex items-center gap-3 mb-1">
          <div className="w-10 h-10 rounded-xl bg-purple-500/20 flex items-center justify-center text-purple-500">
            <Play className="w-5 h-5 fill-current" />
          </div>
          <h3 className="text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-slate-700 to-slate-900 dark:from-slate-100 dark:to-slate-300">Submit New Job</h3>
        </div>
        <p className="text-sm text-slate-500 dark:text-slate-400 mb-6 pl-13">Orchestrate a new task across the distribution network.</p>
        
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="relative group">
            <div className="absolute -inset-0.5 bg-gradient-to-r from-purple-500/50 to-mint-500/50 rounded-2xl blur opacity-25 group-focus-within:opacity-100 transition duration-500"></div>
            <textarea
              className="relative w-full h-48 bg-white/50 dark:bg-slate-900/80 text-slate-800 dark:text-mint-200 font-mono text-sm p-5 rounded-2xl outline-none border border-white/60 dark:border-white/10 shadow-inner custom-scrollbar transition-all"
              value={payloadText}
              onChange={(e) => setPayloadText(e.target.value)}
              spellCheck={false}
              placeholder='{ "type": "...", "data": "..." }'
            />
          </div>
          
          {error && <div className="text-red-500 text-xs font-bold px-2 animate-pulse">{error}</div>}
          
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full relative group"
          >
            <div className="absolute -inset-1 bg-gradient-to-r from-purple-600 to-mint-600 rounded-2xl blur opacity-40 group-hover:opacity-75 transition duration-300"></div>
            <div className="relative flex items-center justify-center gap-3 py-4 px-6 bg-gradient-to-r from-purple-600 to-mint-600 text-white rounded-2xl font-bold hover:scale-[1.01] transition-all disabled:opacity-50 disabled:scale-100">
              {isSubmitting ? (
                <>
                  <div className="w-5 h-5 border-3 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Submitting...</span>
                </>
              ) : (
                <>
                  <Play className="w-5 h-5 fill-white" />
                  <span className="tracking-wide">EXECUTE TASK</span>
                </>
              )}
            </div>
          </button>
        </form>
      </div>
    </div>
  );
};

export default SubmitJobModal;
